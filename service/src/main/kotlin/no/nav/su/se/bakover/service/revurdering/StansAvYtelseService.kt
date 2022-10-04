package no.nav.su.se.bakover.service.revurdering

import arrow.core.Either
import arrow.core.getOrHandle
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.NavIdentBruker
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.log
import no.nav.su.se.bakover.common.persistence.TransactionContext
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.oppdrag.SimulerUtbetalingRequest
import no.nav.su.se.bakover.domain.oppdrag.UtbetalRequest
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.revurdering.RevurderingRepo
import no.nav.su.se.bakover.domain.revurdering.StansAvYtelseRevurdering
import no.nav.su.se.bakover.domain.statistikk.StatistikkEvent
import no.nav.su.se.bakover.domain.statistikk.StatistikkEventObserver
import no.nav.su.se.bakover.domain.statistikk.notify
import no.nav.su.se.bakover.domain.vedtak.GjeldendeVedtaksdata
import no.nav.su.se.bakover.domain.vedtak.VedtakSomKanRevurderes
import no.nav.su.se.bakover.service.revurdering.IverksettStansAvYtelseTransactionException.Companion.exception
import no.nav.su.se.bakover.service.revurdering.StansAvYtelseTransactionException.Companion.exception
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
) {
    private val observers: MutableList<StatistikkEventObserver> = mutableListOf()

    fun addObserver(eventObserver: StatistikkEventObserver) {
        observers.add(eventObserver)
    }

    fun stansAvYtelse(
        request: StansYtelseRequest,
        transactionContext: TransactionContext,
    ): StansAvYtelseITransaksjonResponse {
        val simulertRevurdering = when (request) {
            is StansYtelseRequest.Oppdater -> {
                val sak = sakService.hentSak(
                    sakId = request.sakId,
                    sessionContext = transactionContext,
                ).getOrHandle { throw KunneIkkeStanseYtelse.FantIkkeSak.exception() }

                val eksisterende = sak.hentRevurdering(request.revurderingId)
                    .getOrHandle { throw KunneIkkeStanseYtelse.FantIkkeRevurdering.exception() }

                val gjeldendeVedtaksdata = kopierGjeldendeVedtaksdata(
                    sak = sak,
                    fraOgMed = request.fraOgMed,
                ).getOrHandle { throw it.exception() }

                val simulering = simuler(request).getOrHandle { throw it.exception() }

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

                    else -> throw KunneIkkeStanseYtelse.UgyldigTypeForOppdatering(eksisterende::class).exception()
                }
            }

            is StansYtelseRequest.Opprett -> {
                val sak = sakService.hentSak(
                    sakId = request.sakId,
                    sessionContext = transactionContext,
                ).getOrHandle { throw KunneIkkeStanseYtelse.FantIkkeSak.exception() }

                if (!sak.kanOppretteBehandling()) {
                    throw KunneIkkeStanseYtelse.SakHarÅpenBehandling.exception()
                }

                val gjeldendeVedtaksdata = kopierGjeldendeVedtaksdata(
                    sak = sak,
                    fraOgMed = request.fraOgMed,
                ).getOrHandle { throw it.exception() }

                // TODO send sak
                val simulering = simuler(request).getOrHandle { throw it.exception() }

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

        revurderingRepo.lagre(
            revurdering = simulertRevurdering,
            transactionContext = transactionContext,
        )

        return StansAvYtelseITransaksjonResponse(
            revurdering = simulertRevurdering,
            sendStatistikkCallback = {
                observers.notify(StatistikkEvent.Behandling.Stans.Opprettet(simulertRevurdering))
            },
        )
    }

    fun iverksettStansAvYtelse(
        revurderingId: UUID,
        attestant: NavIdentBruker.Attestant,
        sessionContext: TransactionContext,
    ): IverksettStansAvYtelseITransaksjonResponse {
        val revurdering = revurderingRepo.hent(
            id = revurderingId,
            sessionContext = sessionContext,
        ) ?: throw KunneIkkeIverksetteStansYtelse.FantIkkeRevurdering.exception()

        return when (revurdering) {
            is StansAvYtelseRevurdering.SimulertStansAvYtelse -> {
                val iverksattRevurdering = revurdering.iverksett(
                    Attestering.Iverksatt(
                        attestant = attestant,
                        opprettet = Tidspunkt.now(clock),
                    ),
                ).getOrHandle {
                    throw KunneIkkeIverksetteStansYtelse.SimuleringIndikererFeilutbetaling.exception()
                }

                val stansUtbetaling = utbetalingService.klargjørStans(
                    request = UtbetalRequest.Stans(
                        request = SimulerUtbetalingRequest.Stans(
                            sakId = iverksattRevurdering.sakId,
                            saksbehandler = iverksattRevurdering.attesteringer.hentSisteAttestering().attestant,
                            stansdato = iverksattRevurdering.periode.fraOgMed,
                        ),
                        simulering = iverksattRevurdering.simulering,
                    ),
                    transactionContext = sessionContext,
                ).getOrHandle {
                    throw KunneIkkeIverksetteStansYtelse.KunneIkkeUtbetale(it).exception()
                }

                val vedtak = VedtakSomKanRevurderes.from(iverksattRevurdering, stansUtbetaling.utbetaling.id, clock)

                revurderingRepo.lagre(
                    revurdering = iverksattRevurdering,
                    transactionContext = sessionContext,
                )
                vedtakService.lagre(
                    vedtak = vedtak,
                    sessionContext = sessionContext,
                )

                IverksettStansAvYtelseITransaksjonResponse(
                    revurdering = iverksattRevurdering,
                    vedtak = vedtak,
                    sendUtbetalingCallback = stansUtbetaling::sendUtbetaling,
                    sendStatistikkCallback = {
                        observers.notify(StatistikkEvent.Behandling.Stans.Iverksatt(vedtak))
                        observers.notify(StatistikkEvent.Stønadsvedtak(vedtak))
                    },
                )
            }
            else -> {
                throw KunneIkkeIverksetteStansYtelse.UgyldigTilstand(faktiskTilstand = revurdering::class).exception()
            }
        }
    }

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
