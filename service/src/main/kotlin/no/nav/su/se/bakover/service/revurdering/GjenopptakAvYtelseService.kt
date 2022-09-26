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
import no.nav.su.se.bakover.domain.revurdering.GjenopptaYtelseRevurdering
import no.nav.su.se.bakover.domain.revurdering.RevurderingRepo
import no.nav.su.se.bakover.domain.statistikk.StatistikkEvent
import no.nav.su.se.bakover.domain.statistikk.StatistikkEventObserver
import no.nav.su.se.bakover.domain.vedtak.GjeldendeVedtaksdata
import no.nav.su.se.bakover.domain.vedtak.VedtakRepo
import no.nav.su.se.bakover.domain.vedtak.VedtakSomKanRevurderes
import no.nav.su.se.bakover.service.sak.SakService
import no.nav.su.se.bakover.service.utbetaling.UtbetalingService
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

class GjenopptakAvYtelseService(
    private val utbetalingService: UtbetalingService,
    private val revurderingRepo: RevurderingRepo,
    private val clock: Clock,
    private val vedtakRepo: VedtakRepo,
    private val sakService: SakService,
    private val sessionFactory: SessionFactory,
) {
    private val observers: MutableList<StatistikkEventObserver> = mutableListOf()

    fun addObserver(eventObserver: StatistikkEventObserver) {
        observers.add(eventObserver)
    }

    fun gjenopptaYtelse(request: GjenopptaYtelseRequest): Either<KunneIkkeGjenopptaYtelse, GjenopptaYtelseRevurdering.SimulertGjenopptakAvYtelse> {
        val sak = sakService.hentSak(request.sakId)
            .getOrHandle { return KunneIkkeGjenopptaYtelse.FantIkkeSak.left() }

        val sisteVedtak = sak.vedtakstidslinje()
            .lastOrNull()?.originaltVedtak ?: return KunneIkkeGjenopptaYtelse.FantIngenVedtak.left()

        if (sisteVedtak !is VedtakSomKanRevurderes.EndringIYtelse.StansAvYtelse) {
            return KunneIkkeGjenopptaYtelse.SisteVedtakErIkkeStans.left()
        } else {
            val simulertRevurdering = when (request) {
                is GjenopptaYtelseRequest.Oppdater -> {
                    val update = sak.hentRevurdering(request.revurderingId)
                        .getOrHandle { return KunneIkkeGjenopptaYtelse.FantIkkeRevurdering.left() }

                    val gjeldendeVedtaksdata: GjeldendeVedtaksdata = kopierGjeldendeVedtaksdata(
                        sak = sak,
                        fraOgMed = sisteVedtak.periode.fraOgMed,
                    ).getOrHandle { return it.left() }

                    val simulering = simuler(sak, request).getOrHandle { return it.left() }

                    when (update) {
                        is GjenopptaYtelseRevurdering.SimulertGjenopptakAvYtelse -> {
                            GjenopptaYtelseRevurdering.SimulertGjenopptakAvYtelse(
                                id = update.id,
                                opprettet = update.opprettet,
                                periode = gjeldendeVedtaksdata.garantertSammenhengendePeriode(),
                                grunnlagsdata = gjeldendeVedtaksdata.grunnlagsdata,
                                vilkårsvurderinger = gjeldendeVedtaksdata.vilkårsvurderinger.tilVilkårsvurderingerRevurdering(),
                                tilRevurdering = gjeldendeVedtaksdata.gjeldendeVedtakPåDato(sisteVedtak.periode.fraOgMed)!!.id,
                                saksbehandler = request.saksbehandler,
                                simulering = simulering.simulering,
                                revurderingsårsak = request.revurderingsårsak,
                                sakinfo = sak.info(),
                            )
                        }

                        else -> return KunneIkkeGjenopptaYtelse.UgyldigTypeForOppdatering(update::class).left()
                    }
                }

                is GjenopptaYtelseRequest.Opprett -> {
                    if (!sak.kanOppretteBehandling()) {
                        return KunneIkkeGjenopptaYtelse.SakHarÅpenBehandling.left()
                    }

                    val gjeldendeVedtaksdata: GjeldendeVedtaksdata = kopierGjeldendeVedtaksdata(
                        sak = sak,
                        fraOgMed = sisteVedtak.periode.fraOgMed,
                    ).getOrHandle { return it.left() }

                    val simulering = simuler(sak, request).getOrHandle { return it.left() }

                    GjenopptaYtelseRevurdering.SimulertGjenopptakAvYtelse(
                        id = UUID.randomUUID(),
                        opprettet = Tidspunkt.now(clock),
                        periode = gjeldendeVedtaksdata.garantertSammenhengendePeriode(),
                        grunnlagsdata = gjeldendeVedtaksdata.grunnlagsdata,
                        vilkårsvurderinger = gjeldendeVedtaksdata.vilkårsvurderinger.tilVilkårsvurderingerRevurdering(),
                        tilRevurdering = gjeldendeVedtaksdata.gjeldendeVedtakPåDato(sisteVedtak.periode.fraOgMed)!!.id,
                        saksbehandler = request.saksbehandler,
                        simulering = simulering.simulering,
                        revurderingsårsak = request.revurderingsårsak,
                        sakinfo = sak.info(),
                    )
                }
            }

            revurderingRepo.lagre(simulertRevurdering)
            observers.forEach { observer ->
                observer.handle(
                    StatistikkEvent.Behandling.Gjenoppta.Opprettet(simulertRevurdering),
                )
            }

            return simulertRevurdering.right()
        }
    }

    fun iverksettGjenopptakAvYtelse(
        revurderingId: UUID,
        attestant: NavIdentBruker.Attestant,
    ): Either<KunneIkkeIverksetteGjenopptakAvYtelse, GjenopptaYtelseRevurdering.IverksattGjenopptakAvYtelse> {
        val revurdering = revurderingRepo.hent(revurderingId)
            ?: return KunneIkkeIverksetteGjenopptakAvYtelse.FantIkkeRevurdering.left()

        return when (revurdering) {
            is GjenopptaYtelseRevurdering.SimulertGjenopptakAvYtelse -> {
                val iverksattRevurdering = revurdering.iverksett(
                    Attestering.Iverksatt(
                        attestant,
                        Tidspunkt.now(clock),
                    ),
                ).getOrHandle { return KunneIkkeIverksetteGjenopptakAvYtelse.SimuleringIndikererFeilutbetaling.left() }

                Either.catch {
                    sessionFactory.withTransactionContext { tx ->
                        val gjenopptak = utbetalingService.gjenopptaUtbetalinger(
                            request = UtbetalRequest.Gjenopptak(
                                sakId = iverksattRevurdering.sakId,
                                saksbehandler = iverksattRevurdering.attesteringer.hentSisteAttestering().attestant,
                                simulering = iverksattRevurdering.simulering,
                            ),
                            transactionContext = tx,
                        ).getOrHandle {
                            throw IverksettTransactionException(
                                """Feil:$it ved opprettelse av utbetaling for revurdering:$revurderingId - ruller tilbake.""",
                                KunneIkkeIverksetteGjenopptakAvYtelse.KunneIkkeUtbetale(it),
                            )
                        }

                        val vedtak = VedtakSomKanRevurderes.from(iverksattRevurdering, gjenopptak.utbetaling.id, clock)

                        revurderingRepo.lagre(
                            revurdering = iverksattRevurdering,
                            transactionContext = tx,
                        )
                        vedtakRepo.lagre(
                            vedtak = vedtak,
                            sessionContext = tx,
                        )

                        gjenopptak.sendUtbetalingTilOS()
                            .getOrHandle {
                                throw IverksettTransactionException(
                                    """Feil:$it ved publisering av utbetaling for revurdering:$revurderingId - ruller tilbake.""",
                                    KunneIkkeIverksetteGjenopptakAvYtelse.KunneIkkeUtbetale(it),
                                )
                            }

                        iverksattRevurdering to vedtak
                    }
                }.mapLeft {
                    log.error("Feil ved iverksetting av gjenopptak for revurdering: $revurderingId", it)
                    when (it) {
                        is IverksettTransactionException -> it.feil
                        else -> KunneIkkeIverksetteGjenopptakAvYtelse.LagringFeilet
                    }
                }.map { (iverksattRevurdering, vedtak) ->
                    observers.forEach { observer ->
                        observer.handle(StatistikkEvent.Behandling.Gjenoppta.Iverksatt(vedtak))
                        observer.handle(StatistikkEvent.Stønadsvedtak(vedtak))
                    }
                    iverksattRevurdering
                }
            }
            else -> { KunneIkkeIverksetteGjenopptakAvYtelse.UgyldigTilstand(faktiskTilstand = revurdering::class).left() }
        }
    }
    private data class IverksettTransactionException(
        override val message: String,
        val feil: KunneIkkeIverksetteGjenopptakAvYtelse,
    ) : RuntimeException(message)

    private fun kopierGjeldendeVedtaksdata(
        sak: Sak,
        fraOgMed: LocalDate,
    ): Either<KunneIkkeGjenopptaYtelse, GjeldendeVedtaksdata> {
        return sak.kopierGjeldendeVedtaksdata(
            fraOgMed = fraOgMed,
            clock = clock,
        ).getOrHandle {
            log.error("Kunne ikke opprette revurdering for gjenopptak av ytelse, årsak: $it")
            return KunneIkkeGjenopptaYtelse.KunneIkkeOppretteRevurdering.left()
        }.also {
            if (!it.tidslinjeForVedtakErSammenhengende()) {
                log.error("Kunne ikke opprette revurdering for gjenopptak av ytelse, årsak: tidslinje er ikke sammenhengende.")
                return KunneIkkeGjenopptaYtelse.KunneIkkeOppretteRevurdering.left()
            }
        }.right()
    }

    private fun simuler(
        sak: Sak,
        request: GjenopptaYtelseRequest,
    ): Either<KunneIkkeGjenopptaYtelse, Utbetaling.SimulertUtbetaling> =
        utbetalingService.simulerGjenopptak(
            request = SimulerUtbetalingRequest.Gjenopptak(
                saksbehandler = request.saksbehandler,
                sak = sak,
            ),
        ).getOrHandle {
            log.warn("Kunne ikke opprette revurdering for gjenopptak av ytelse, årsak: $it")
            return KunneIkkeGjenopptaYtelse.KunneIkkeSimulere(it).left()
        }.right()
}
