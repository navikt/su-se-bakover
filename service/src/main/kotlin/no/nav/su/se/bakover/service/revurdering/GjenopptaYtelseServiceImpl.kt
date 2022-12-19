package no.nav.su.se.bakover.service.revurdering

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.getOrHandle
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.NavIdentBruker
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.log
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimulerGjenopptakFeil
import no.nav.su.se.bakover.domain.oppdrag.utbetaling.UtbetalGjenopptakFeil
import no.nav.su.se.bakover.domain.revurdering.GjenopptaYtelseRevurdering
import no.nav.su.se.bakover.domain.revurdering.RevurderingRepo
import no.nav.su.se.bakover.domain.revurdering.gjenopptak.GjenopptaYtelseRequest
import no.nav.su.se.bakover.domain.revurdering.gjenopptak.GjenopptaYtelseService
import no.nav.su.se.bakover.domain.revurdering.gjenopptak.KunneIkkeIverksetteGjenopptakAvYtelseForRevurdering
import no.nav.su.se.bakover.domain.revurdering.gjenopptak.KunneIkkeSimulereGjenopptakAvYtelse
import no.nav.su.se.bakover.domain.sak.SakService
import no.nav.su.se.bakover.domain.sak.lagUtbetalingForGjenopptak
import no.nav.su.se.bakover.domain.sak.simulerUtbetaling
import no.nav.su.se.bakover.domain.statistikk.StatistikkEvent
import no.nav.su.se.bakover.domain.statistikk.StatistikkEventObserver
import no.nav.su.se.bakover.domain.statistikk.notify
import no.nav.su.se.bakover.domain.vedtak.GjeldendeVedtaksdata
import no.nav.su.se.bakover.domain.vedtak.VedtakRepo
import no.nav.su.se.bakover.domain.vedtak.VedtakSomKanRevurderes
import no.nav.su.se.bakover.service.utbetaling.UtbetalingService
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

class GjenopptaYtelseServiceImpl(
    private val utbetalingService: UtbetalingService,
    private val revurderingRepo: RevurderingRepo,
    private val clock: Clock,
    private val vedtakRepo: VedtakRepo,
    private val sakService: SakService,
    private val sessionFactory: SessionFactory,
) : GjenopptaYtelseService {
    private val observers: MutableList<StatistikkEventObserver> = mutableListOf()

    fun addObserver(eventObserver: StatistikkEventObserver) {
        observers.add(eventObserver)
    }

    override fun gjenopptaYtelse(request: GjenopptaYtelseRequest): Either<KunneIkkeSimulereGjenopptakAvYtelse, GjenopptaYtelseRevurdering.SimulertGjenopptakAvYtelse> {
        val sak = sakService.hentSak(request.sakId)
            .getOrHandle { return KunneIkkeSimulereGjenopptakAvYtelse.FantIkkeSak.left() }

        val sisteVedtakPåTidslinje = sak.vedtakstidslinje()
            .tidslinje
            .lastOrNull() ?: return KunneIkkeSimulereGjenopptakAvYtelse.FantIngenVedtak.left()

        if (sisteVedtakPåTidslinje.originaltVedtak !is VedtakSomKanRevurderes.EndringIYtelse.StansAvYtelse) {
            return KunneIkkeSimulereGjenopptakAvYtelse.SisteVedtakErIkkeStans.left()
        } else {
            val simulertRevurdering = when (request) {
                is GjenopptaYtelseRequest.Oppdater -> {
                    val update = sak.hentRevurdering(request.revurderingId)
                        .getOrHandle { return KunneIkkeSimulereGjenopptakAvYtelse.FantIkkeRevurdering.left() }

                    val gjeldendeVedtaksdata: GjeldendeVedtaksdata = kopierGjeldendeVedtaksdata(
                        sak = sak,
                        fraOgMed = sisteVedtakPåTidslinje.periode.fraOgMed,
                    ).getOrHandle { return it.left() }

                    val simulertUtbetaling = simulerGjenopptak(
                        sak = sak,
                        gjenopptak = null,
                        behandler = request.saksbehandler,
                    ).getOrHandle {
                        return KunneIkkeSimulereGjenopptakAvYtelse.KunneIkkeSimulere(it).left()
                    }

                    when (update) {
                        is GjenopptaYtelseRevurdering.SimulertGjenopptakAvYtelse -> {
                            GjenopptaYtelseRevurdering.SimulertGjenopptakAvYtelse(
                                id = update.id,
                                opprettet = update.opprettet,
                                periode = gjeldendeVedtaksdata.garantertSammenhengendePeriode(),
                                grunnlagsdata = gjeldendeVedtaksdata.grunnlagsdata,
                                vilkårsvurderinger = gjeldendeVedtaksdata.vilkårsvurderinger.tilVilkårsvurderingerRevurdering(),
                                tilRevurdering = gjeldendeVedtaksdata.gjeldendeVedtakPåDato(sisteVedtakPåTidslinje.periode.fraOgMed)!!.id,
                                saksbehandler = request.saksbehandler,
                                simulering = simulertUtbetaling.simulering,
                                revurderingsårsak = request.revurderingsårsak,
                                sakinfo = sak.info(),
                            )
                        }

                        else -> return KunneIkkeSimulereGjenopptakAvYtelse.UgyldigTypeForOppdatering(update::class).left()
                    }
                }

                is GjenopptaYtelseRequest.Opprett -> {
                    if (!sak.harIngenÅpneBehandlinger()) {
                        return KunneIkkeSimulereGjenopptakAvYtelse.SakHarÅpenBehandling.left()
                    }

                    val gjeldendeVedtaksdata: GjeldendeVedtaksdata = kopierGjeldendeVedtaksdata(
                        sak = sak,
                        fraOgMed = sisteVedtakPåTidslinje.periode.fraOgMed,
                    ).getOrHandle { return it.left() }

                    val simulertUtbetaling = simulerGjenopptak(
                        sak = sak,
                        gjenopptak = null,
                        behandler = request.saksbehandler,
                    ).getOrHandle {
                        return KunneIkkeSimulereGjenopptakAvYtelse.KunneIkkeSimulere(it).left()
                    }

                    GjenopptaYtelseRevurdering.SimulertGjenopptakAvYtelse(
                        id = UUID.randomUUID(),
                        opprettet = Tidspunkt.now(clock),
                        periode = gjeldendeVedtaksdata.garantertSammenhengendePeriode(),
                        grunnlagsdata = gjeldendeVedtaksdata.grunnlagsdata,
                        vilkårsvurderinger = gjeldendeVedtaksdata.vilkårsvurderinger.tilVilkårsvurderingerRevurdering(),
                        tilRevurdering = gjeldendeVedtaksdata.gjeldendeVedtakPåDato(sisteVedtakPåTidslinje.periode.fraOgMed)!!.id,
                        saksbehandler = request.saksbehandler,
                        simulering = simulertUtbetaling.simulering,
                        revurderingsårsak = request.revurderingsårsak,
                        sakinfo = sak.info(),
                    )
                }
            }

            revurderingRepo.lagre(simulertRevurdering)
            observers.notify(StatistikkEvent.Behandling.Gjenoppta.Opprettet(simulertRevurdering))

            return simulertRevurdering.right()
        }
    }

    private fun simulerGjenopptak(sak: Sak, gjenopptak: GjenopptaYtelseRevurdering?, behandler: NavIdentBruker): Either<SimulerGjenopptakFeil, Utbetaling.SimulertUtbetaling> {
        return sak.lagUtbetalingForGjenopptak(
            saksbehandler = behandler,
            clock = clock,
        ).mapLeft {
            SimulerGjenopptakFeil.KunneIkkeGenerereUtbetaling(it)
        }.flatMap { utbetaling ->
            sak.simulerUtbetaling(
                utbetalingForSimulering = utbetaling,
                periode = Periode.create(
                    utbetaling.tidligsteDato(),
                    utbetaling.senesteDato(),
                ),
                simuler = utbetalingService::simulerUtbetaling,
                kontrollerMotTidligereSimulering = gjenopptak?.simulering,
                clock = clock,
            ).mapLeft {
                SimulerGjenopptakFeil.KunneIkkeSimulere(it)
            }
        }
    }

    override fun iverksettGjenopptakAvYtelse(
        revurderingId: UUID,
        attestant: NavIdentBruker.Attestant,
    ): Either<KunneIkkeIverksetteGjenopptakAvYtelseForRevurdering, GjenopptaYtelseRevurdering.IverksattGjenopptakAvYtelse> {
        val sak = sakService.hentSakForRevurdering(revurderingId)

        val revurdering = sak.hentRevurdering(revurderingId)
            .getOrHandle { return KunneIkkeIverksetteGjenopptakAvYtelseForRevurdering.FantIkkeRevurdering.left() }

        return when (revurdering) {
            is GjenopptaYtelseRevurdering.SimulertGjenopptakAvYtelse -> {
                val iverksattRevurdering = revurdering.iverksett(
                    Attestering.Iverksatt(
                        attestant,
                        Tidspunkt.now(clock),
                    ),
                ).getOrHandle { return KunneIkkeIverksetteGjenopptakAvYtelseForRevurdering.SimuleringIndikererFeilutbetaling.left() }

                Either.catch {
                    val simulertUtbetaling = simulerGjenopptak(
                        sak = sak,
                        gjenopptak = iverksattRevurdering,
                        behandler = revurdering.saksbehandler,
                    ).getOrHandle {
                        throw IverksettTransactionException(
                            """Feil:$it ved opprettelse av utbetaling for revurdering:$revurderingId - ruller tilbake.""",
                            KunneIkkeIverksetteGjenopptakAvYtelseForRevurdering.KunneIkkeUtbetale(UtbetalGjenopptakFeil.KunneIkkeSimulere(it)),
                        )
                    }

                    sessionFactory.withTransactionContext { tx ->
                        val gjenopptak = utbetalingService.klargjørUtbetaling(
                            utbetaling = simulertUtbetaling,
                            transactionContext = tx,
                        ).getOrHandle {
                            throw IverksettTransactionException(
                                """Feil:$it ved opprettelse av utbetaling for revurdering:$revurderingId - ruller tilbake.""",
                                KunneIkkeIverksetteGjenopptakAvYtelseForRevurdering.KunneIkkeUtbetale(UtbetalGjenopptakFeil.KunneIkkeUtbetale(it)),
                            )
                        }

                        val vedtak = VedtakSomKanRevurderes.from(
                            revurdering = iverksattRevurdering,
                            utbetalingId = gjenopptak.utbetaling.id,
                            clock = clock,
                        )

                        revurderingRepo.lagre(
                            revurdering = iverksattRevurdering,
                            transactionContext = tx,
                        )
                        vedtakRepo.lagreITransaksjon(
                            vedtak = vedtak,
                            sessionContext = tx,
                        )

                        gjenopptak.sendUtbetaling()
                            .getOrHandle {
                                throw IverksettTransactionException(
                                    """Feil:$it ved publisering av utbetaling for revurdering:$revurderingId - ruller tilbake.""",
                                    KunneIkkeIverksetteGjenopptakAvYtelseForRevurdering.KunneIkkeUtbetale(UtbetalGjenopptakFeil.KunneIkkeUtbetale(it)),
                                )
                            }

                        vedtak
                    }
                }.mapLeft {
                    log.error("Feil ved iverksetting av gjenopptak for revurdering: $revurderingId", it)
                    when (it) {
                        is IverksettTransactionException -> it.feil
                        else -> KunneIkkeIverksetteGjenopptakAvYtelseForRevurdering.LagringFeilet
                    }
                }.map { vedtak ->
                    observers.notify(StatistikkEvent.Behandling.Gjenoppta.Iverksatt(vedtak))
                    // TODO jah: Vi har gjort endringer på saken underveis - endret regulering, ny utbetaling og nytt vedtak - uten at selve saken blir oppdatert underveis. Når saken returnerer en oppdatert versjon av seg selv for disse tilfellene kan vi fjerne det ekstra kallet til hentSak.
                    observers.notify(StatistikkEvent.Stønadsvedtak(vedtak) { sakService.hentSak(sak.id).orNull()!! })
                    iverksattRevurdering
                }
            }
            else -> { KunneIkkeIverksetteGjenopptakAvYtelseForRevurdering.UgyldigTilstand(faktiskTilstand = revurdering::class).left() }
        }
    }
    private data class IverksettTransactionException(
        override val message: String,
        val feil: KunneIkkeIverksetteGjenopptakAvYtelseForRevurdering,
    ) : RuntimeException(message)

    private fun kopierGjeldendeVedtaksdata(
        sak: Sak,
        fraOgMed: LocalDate,
    ): Either<KunneIkkeSimulereGjenopptakAvYtelse, GjeldendeVedtaksdata> {
        return sak.kopierGjeldendeVedtaksdata(
            fraOgMed = fraOgMed,
            clock = clock,
        ).getOrHandle {
            log.error("Kunne ikke opprette revurdering for gjenopptak av ytelse, årsak: $it")
            return KunneIkkeSimulereGjenopptakAvYtelse.KunneIkkeOppretteRevurdering.left()
        }.also {
            if (!it.tidslinjeForVedtakErSammenhengende()) {
                log.error("Kunne ikke opprette revurdering for gjenopptak av ytelse, årsak: tidslinje er ikke sammenhengende.")
                return KunneIkkeSimulereGjenopptakAvYtelse.KunneIkkeOppretteRevurdering.left()
            }
        }.right()
    }
}
