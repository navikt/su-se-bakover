package no.nav.su.se.bakover.service.revurdering

import arrow.core.Either
import arrow.core.getOrHandle
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.log
import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.oppdrag.SimulerUtbetalingRequest
import no.nav.su.se.bakover.domain.oppdrag.UtbetalRequest
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.revurdering.RevurderingRepo
import no.nav.su.se.bakover.domain.revurdering.StansAvYtelseRevurdering
import no.nav.su.se.bakover.domain.statistikk.StatistikkEvent
import no.nav.su.se.bakover.domain.statistikk.StatistikkEventObserver
import no.nav.su.se.bakover.domain.vedtak.GjeldendeVedtaksdata
import no.nav.su.se.bakover.domain.vedtak.VedtakSomKanRevurderes
import no.nav.su.se.bakover.service.sak.SakService
import no.nav.su.se.bakover.service.utbetaling.UtbetalingService
import no.nav.su.se.bakover.service.vedtak.VedtakService
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

internal class StansAvYtelseService(
    private val utbetalingService: UtbetalingService,
    private val revurderingRepo: RevurderingRepo,
    private val vedtakService: VedtakService,
    private val sakService: SakService,
    private val clock: Clock,
    private val sessionFactory: SessionFactory,
) {
    private val observers: MutableList<StatistikkEventObserver> = mutableListOf()

    fun addObserver(eventObserver: StatistikkEventObserver) {
        observers.add(eventObserver)
    }

    fun stansAvYtelse(
        request: StansYtelseRequest,
    ): Either<KunneIkkeStanseYtelse, StansAvYtelseRevurdering.SimulertStansAvYtelse> {
        val simulertRevurdering = when (request) {
            is StansYtelseRequest.Oppdater -> {
                val sak = sakService.hentSak(request.sakId)
                    .getOrHandle { return KunneIkkeStanseYtelse.FantIkkeSak.left() }

                val eksisterende = sak.hentRevurdering(request.revurderingId)
                    .getOrHandle { return KunneIkkeStanseYtelse.FantIkkeRevurdering.left() }

                val gjeldendeVedtaksdata = kopierGjeldendeVedtaksdata(
                    sak = sak,
                    fraOgMed = request.fraOgMed,
                ).getOrHandle { return it.left() }

                val simulering = simuler(request).getOrHandle { return it.left() }

                when (eksisterende) {
                    is StansAvYtelseRevurdering.SimulertStansAvYtelse -> {
                        eksisterende.copy(
                            periode = gjeldendeVedtaksdata.garantertSammenhengendePeriode(),
                            grunnlagsdata = gjeldendeVedtaksdata.grunnlagsdata,
                            vilkårsvurderinger = gjeldendeVedtaksdata.vilkårsvurderinger,
                            tilRevurdering = gjeldendeVedtaksdata.gjeldendeVedtakPåDato(request.fraOgMed)!!.id,
                            saksbehandler = request.saksbehandler,
                            simulering = simulering.simulering,
                            revurderingsårsak = request.revurderingsårsak,
                        )
                    }

                    else -> return KunneIkkeStanseYtelse.UgyldigTypeForOppdatering(eksisterende::class).left()
                }
            }

            is StansYtelseRequest.Opprett -> {
                val sak = sakService.hentSak(request.sakId)
                    .getOrHandle { return KunneIkkeStanseYtelse.FantIkkeSak.left() }

                if (!sak.kanOppretteBehandling()) {
                    return KunneIkkeStanseYtelse.SakHarÅpenBehandling.left()
                }

                val gjeldendeVedtaksdata = kopierGjeldendeVedtaksdata(
                    sak = sak,
                    fraOgMed = request.fraOgMed,
                ).getOrHandle { return it.left() }

                val simulering = simuler(request)
                    .getOrHandle { return it.left() }

                StansAvYtelseRevurdering.SimulertStansAvYtelse(
                    id = UUID.randomUUID(),
                    opprettet = Tidspunkt.now(clock),
                    periode = gjeldendeVedtaksdata.garantertSammenhengendePeriode(),
                    grunnlagsdata = gjeldendeVedtaksdata.grunnlagsdata,
                    vilkårsvurderinger = gjeldendeVedtaksdata.vilkårsvurderinger,
                    tilRevurdering = gjeldendeVedtaksdata.gjeldendeVedtakPåDato(request.fraOgMed)!!.id,
                    saksbehandler = request.saksbehandler,
                    simulering = simulering.simulering,
                    revurderingsårsak = request.revurderingsårsak,
                    sakinfo = sak.info(),
                )
            }
        }

        revurderingRepo.lagre(simulertRevurdering)
        observers.forEach { observer -> observer.handle(StatistikkEvent.Behandling.Stans.Opprettet(simulertRevurdering)) }

        return simulertRevurdering.right()
    }

    fun iverksettStansAvYtelse(
        revurderingId: UUID,
        attestant: NavIdentBruker.Attestant,
    ): Either<KunneIkkeIverksetteStansYtelse, StansAvYtelseRevurdering.IverksattStansAvYtelse> {
        val revurdering = revurderingRepo.hent(revurderingId)
            ?: return KunneIkkeIverksetteStansYtelse.FantIkkeRevurdering.left()

        return when (revurdering) {
            is StansAvYtelseRevurdering.SimulertStansAvYtelse -> {
                val iverksattRevurdering = revurdering.iverksett(
                    Attestering.Iverksatt(
                        attestant = attestant,
                        opprettet = Tidspunkt.now(clock),
                    ),
                ).getOrHandle { return KunneIkkeIverksetteStansYtelse.SimuleringIndikererFeilutbetaling.left() }

                sessionFactory.withTransactionContext { tx ->
                    val stansUtbetaling = utbetalingService.stansUtbetalinger(
                        request = UtbetalRequest.Stans(
                            request = SimulerUtbetalingRequest.Stans(
                                sakId = iverksattRevurdering.sakId,
                                saksbehandler = iverksattRevurdering.attesteringer.hentSisteAttestering().attestant,
                                stansdato = iverksattRevurdering.periode.fraOgMed,
                            ),
                            simulering = iverksattRevurdering.simulering,
                        ),
                        transactionContext = tx,
                    ).getOrHandle { return@withTransactionContext KunneIkkeIverksetteStansYtelse.KunneIkkeUtbetale(it).left() }

                    val vedtak = VedtakSomKanRevurderes.from(iverksattRevurdering, stansUtbetaling.utbetaling.id, clock)

                    revurderingRepo.lagre(
                        revurdering = iverksattRevurdering,
                        transactionContext = tx,
                    )
                    vedtakService.lagre(
                        vedtak = vedtak,
                        sessionContext = tx,
                    )
                    observers.forEach { observer ->
                        observer.handle(StatistikkEvent.Behandling.Stans.Iverksatt(vedtak))
                        observer.handle(StatistikkEvent.Stønadsvedtak(vedtak))
                    }
                    stansUtbetaling.sendUtbetalingTilOS()
                        .mapLeft { throw TriggerRollbackException("""Feil:$it ved publisering av utbetaling for revurdering:$revurderingId - ruller tilbake.""") }

                    iverksattRevurdering.right()
                }
            }
            else -> {
                KunneIkkeIverksetteStansYtelse.UgyldigTilstand(faktiskTilstand = revurdering::class).left()
            }
        }
    }

    data class TriggerRollbackException(val msg: String) : RuntimeException(msg)

    private fun kopierGjeldendeVedtaksdata(
        sak: Sak,
        fraOgMed: LocalDate,
    ): Either<KunneIkkeStanseYtelse, GjeldendeVedtaksdata> {
        return sak.kopierGjeldendeVedtaksdata(
            fraOgMed = fraOgMed,
            clock = clock,
        ).getOrHandle {
            log.error("Kunne ikke opprette revurdering for stans av ytelse, årsak: $it")
            return KunneIkkeStanseYtelse.KunneIkkeOppretteRevurdering.left()
        }.also {
            if (!it.tidslinjeForVedtakErSammenhengende()) {
                log.error("Kunne ikke opprette revurdering for stans av ytelse, årsak: tidslinje er ikke sammenhengende.")
                return KunneIkkeStanseYtelse.KunneIkkeOppretteRevurdering.left()
            }
        }.right()
    }

    private fun simuler(request: StansYtelseRequest): Either<KunneIkkeStanseYtelse, Utbetaling.SimulertUtbetaling> {
        return utbetalingService.simulerStans(
            request = SimulerUtbetalingRequest.Stans(
                sakId = request.sakId,
                saksbehandler = request.saksbehandler,
                stansdato = request.fraOgMed,
            ),
        ).getOrHandle {
            log.warn("Kunne ikke opprette revurdering for stans av ytelse, årsak: $it")
            return KunneIkkeStanseYtelse.SimuleringAvStansFeilet(it).left()
        }.right()
    }
}
