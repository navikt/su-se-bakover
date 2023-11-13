package no.nav.su.se.bakover.service.revurdering

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.domain.attestering.Attestering
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.oppdrag.simulering.ForskjellerMellomUtbetalingOgSimulering
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simuleringsresultat
import no.nav.su.se.bakover.domain.oppdrag.simulering.kontrollsimuler
import no.nav.su.se.bakover.domain.oppdrag.simulering.simulerUtbetaling
import no.nav.su.se.bakover.domain.revurdering.GjenopptaYtelseRevurdering
import no.nav.su.se.bakover.domain.revurdering.gjenopptak.GjenopptaYtelseRequest
import no.nav.su.se.bakover.domain.revurdering.gjenopptak.GjenopptaYtelseService
import no.nav.su.se.bakover.domain.revurdering.gjenopptak.KunneIkkeIverksetteGjenopptakAvYtelseForRevurdering
import no.nav.su.se.bakover.domain.revurdering.gjenopptak.KunneIkkeSimulereGjenopptakAvYtelse
import no.nav.su.se.bakover.domain.revurdering.iverksett.verifiserAtVedtaksmånedeneViRevurdererIkkeHarForandretSeg
import no.nav.su.se.bakover.domain.revurdering.repo.RevurderingRepo
import no.nav.su.se.bakover.domain.revurdering.revurderes.toVedtakSomRevurderesMånedsvis
import no.nav.su.se.bakover.domain.sak.SakService
import no.nav.su.se.bakover.domain.sak.lagUtbetalingForGjenopptak
import no.nav.su.se.bakover.domain.statistikk.StatistikkEvent
import no.nav.su.se.bakover.domain.statistikk.StatistikkEventObserver
import no.nav.su.se.bakover.domain.statistikk.notify
import no.nav.su.se.bakover.domain.vedtak.GjeldendeVedtaksdata
import no.nav.su.se.bakover.domain.vedtak.VedtakRepo
import no.nav.su.se.bakover.domain.vedtak.VedtakSomKanRevurderes
import no.nav.su.se.bakover.domain.vedtak.VedtakStansAvYtelse
import no.nav.su.se.bakover.service.utbetaling.UtbetalingService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
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

    private val log: Logger = LoggerFactory.getLogger(this::class.java)

    private val observers: MutableList<StatistikkEventObserver> = mutableListOf()

    fun addObserver(eventObserver: StatistikkEventObserver) {
        observers.add(eventObserver)
    }

    override fun gjenopptaYtelse(
        request: GjenopptaYtelseRequest,
    ): Either<KunneIkkeSimulereGjenopptakAvYtelse, Pair<GjenopptaYtelseRevurdering.SimulertGjenopptakAvYtelse, ForskjellerMellomUtbetalingOgSimulering?>> {
        val sak = sakService.hentSak(request.sakId)
            .getOrElse { return KunneIkkeSimulereGjenopptakAvYtelse.FantIkkeSak.left() }

        val sisteVedtakPåTidslinje = sak.vedtakstidslinje()?.lastOrNull()
            ?: return KunneIkkeSimulereGjenopptakAvYtelse.FantIngenVedtak.left()

        if (sisteVedtakPåTidslinje.originaltVedtak !is VedtakStansAvYtelse) {
            return KunneIkkeSimulereGjenopptakAvYtelse.SisteVedtakErIkkeStans.left()
        } else {
            val simulertRevurdering: Pair<GjenopptaYtelseRevurdering.SimulertGjenopptakAvYtelse, ForskjellerMellomUtbetalingOgSimulering?> = when (request) {
                is GjenopptaYtelseRequest.Oppdater -> {
                    val update = sak.hentRevurdering(request.revurderingId)
                        .getOrElse { return KunneIkkeSimulereGjenopptakAvYtelse.FantIkkeRevurdering.left() }

                    val gjeldendeVedtaksdata: GjeldendeVedtaksdata = kopierGjeldendeVedtaksdata(
                        sak = sak,
                        fraOgMed = sisteVedtakPåTidslinje.periode.fraOgMed,
                    ).getOrElse { return it.left() }

                    val simuleringsresultat = sak.simulerGjenopptak(request.saksbehandler).getOrElse {
                        return it.left()
                    }
                    val simulertUtbetaling = simuleringsresultat.simulertUtbetaling
                    if (simulertUtbetaling.simulering.harFeilutbetalinger()) {
                        throw IllegalStateException("Simulering av gjenopptak av ytelse skal ikke ha feilutbetalinger")
                    }

                    when (update) {
                        is GjenopptaYtelseRevurdering.SimulertGjenopptakAvYtelse -> {
                            GjenopptaYtelseRevurdering.SimulertGjenopptakAvYtelse(
                                id = update.id,
                                opprettet = update.opprettet,
                                oppdatert = Tidspunkt.now(clock),
                                periode = gjeldendeVedtaksdata.garantertSammenhengendePeriode(),
                                grunnlagsdataOgVilkårsvurderinger = gjeldendeVedtaksdata.grunnlagsdataOgVilkårsvurderinger,
                                tilRevurdering = gjeldendeVedtaksdata.gjeldendeVedtakPåDato(sisteVedtakPåTidslinje.periode.fraOgMed)!!.id,
                                vedtakSomRevurderesMånedsvis = gjeldendeVedtaksdata.toVedtakSomRevurderesMånedsvis(),
                                saksbehandler = request.saksbehandler,
                                simulering = simulertUtbetaling.simulering,
                                revurderingsårsak = request.revurderingsårsak,
                                sakinfo = sak.info(),
                            ) to simuleringsresultat.forskjeller
                        }

                        else -> return KunneIkkeSimulereGjenopptakAvYtelse.UgyldigTypeForOppdatering(update::class)
                            .left()
                    }
                }

                is GjenopptaYtelseRequest.Opprett -> {
                    if (sak.harÅpenGjenopptaksbehandling()) {
                        return KunneIkkeSimulereGjenopptakAvYtelse.FinnesÅpenGjenopptaksbehandling.left()
                    }
                    val gjeldendeVedtaksdata: GjeldendeVedtaksdata = kopierGjeldendeVedtaksdata(
                        sak = sak,
                        fraOgMed = sisteVedtakPåTidslinje.periode.fraOgMed,
                    ).getOrElse { return it.left() }

                    val simuleringsresultat = sak.simulerGjenopptak(request.saksbehandler).getOrElse {
                        return it.left()
                    }
                    val simulertUtbetaling = simuleringsresultat.simulertUtbetaling
                    if (simulertUtbetaling.simulering.harFeilutbetalinger()) {
                        throw IllegalStateException("Simulering av gjenopptak av ytelse skal ikke ha feilutbetalinger")
                    }

                    GjenopptaYtelseRevurdering.SimulertGjenopptakAvYtelse(
                        id = UUID.randomUUID(),
                        opprettet = Tidspunkt.now(clock),
                        oppdatert = Tidspunkt.now(clock),
                        periode = gjeldendeVedtaksdata.garantertSammenhengendePeriode(),
                        grunnlagsdataOgVilkårsvurderinger = gjeldendeVedtaksdata.grunnlagsdataOgVilkårsvurderinger,
                        tilRevurdering = gjeldendeVedtaksdata.gjeldendeVedtakPåDato(sisteVedtakPåTidslinje.periode.fraOgMed)!!.id,
                        vedtakSomRevurderesMånedsvis = gjeldendeVedtaksdata.toVedtakSomRevurderesMånedsvis(),
                        saksbehandler = request.saksbehandler,
                        simulering = simulertUtbetaling.simulering,
                        revurderingsårsak = request.revurderingsårsak,
                        sakinfo = sak.info(),
                    ) to simuleringsresultat.forskjeller
                }
            }

            revurderingRepo.lagre(simulertRevurdering.first)
            observers.notify(StatistikkEvent.Behandling.Gjenoppta.Opprettet(simulertRevurdering.first))

            return simulertRevurdering.right()
        }
    }

    private fun Sak.simulerGjenopptak(
        saksbehandler: NavIdentBruker.Saksbehandler,
    ): Either<KunneIkkeSimulereGjenopptakAvYtelse, Simuleringsresultat> {
        return simulerUtbetaling(
            tidligereUtbetalinger = this.utbetalinger,
            utbetalingForSimulering = this.lagUtbetalingForGjenopptak(
                saksbehandler = saksbehandler,
                clock = clock,
            ).getOrElse {
                return KunneIkkeSimulereGjenopptakAvYtelse.KunneIkkeGenerereUtbetaling(it).left()
            },
            simuler = utbetalingService::simulerUtbetaling,
        ).getOrElse {
            return KunneIkkeSimulereGjenopptakAvYtelse.KunneIkkeSimulere(it).left()
        }.right()
    }

    override fun iverksettGjenopptakAvYtelse(
        revurderingId: UUID,
        attestant: NavIdentBruker.Attestant,
    ): Either<KunneIkkeIverksetteGjenopptakAvYtelseForRevurdering, GjenopptaYtelseRevurdering.IverksattGjenopptakAvYtelse> {
        val sak = sakService.hentSakForRevurdering(revurderingId)

        val revurdering = sak.hentRevurdering(revurderingId)
            .getOrElse { return KunneIkkeIverksetteGjenopptakAvYtelseForRevurdering.FantIkkeRevurdering.left() }

        return when (revurdering) {
            is GjenopptaYtelseRevurdering.SimulertGjenopptakAvYtelse -> {
                val sisteVedtakPåTidslinje = sak.vedtakstidslinje()?.lastOrNull()
                    ?: throw IllegalStateException("Fant siste vedtak på tidslinje ved iverksettelse av stans på sak ${sak.id}")
                val gjeldendeVedtaksdata: GjeldendeVedtaksdata = kopierGjeldendeVedtaksdata(
                    sak = sak,
                    fraOgMed = sisteVedtakPåTidslinje.periode.fraOgMed,
                ).getOrElse { throw IllegalStateException("Fant ikke gjeldende vedtaksdata ved iverksettelse av stans på sak ${sak.id}") }
                val stansperiodeVedIverksettelse = gjeldendeVedtaksdata.garantertSammenhengendePeriode()
                if (stansperiodeVedIverksettelse != revurdering.periode) {
                    return KunneIkkeIverksetteGjenopptakAvYtelseForRevurdering.DetHarKommetNyeOverlappendeVedtak.left()
                }
                if (sak.verifiserAtVedtaksmånedeneViRevurdererIkkeHarForandretSeg(
                        periode = stansperiodeVedIverksettelse,
                        eksisterendeVedtakSomRevurderesMånedsvis = revurdering.vedtakSomRevurderesMånedsvis,
                        clock = clock,
                    ).isLeft()
                ) {
                    return KunneIkkeIverksetteGjenopptakAvYtelseForRevurdering.DetHarKommetNyeOverlappendeVedtak.left()
                }
                val iverksattRevurdering = revurdering.iverksett(
                    Attestering.Iverksatt(
                        attestant,
                        Tidspunkt.now(clock),
                    ),
                )
                    .getOrElse { return KunneIkkeIverksetteGjenopptakAvYtelseForRevurdering.SimuleringIndikererFeilutbetaling.left() }

                Either.catch {
                    val simulertUtbetaling = sak.lagUtbetalingForGjenopptak(
                        // TODO jah: Det er vel mest riktig at man sender attestanten for kontrollsimuleringen? Men usikker på om det har noen betydning.
                        saksbehandler = iverksattRevurdering.saksbehandler,
                        clock = clock,
                    ).getOrElse {
                        return KunneIkkeIverksetteGjenopptakAvYtelseForRevurdering.KunneIkkeGenerereUtbetaling(it)
                            .left()
                    }.let {
                        kontrollsimuler(
                            utbetalingForSimulering = it,
                            simuler = utbetalingService::simulerUtbetaling,
                            saksbehandlersSimulering = iverksattRevurdering.simulering,
                        ).getOrElse {
                            return KunneIkkeIverksetteGjenopptakAvYtelseForRevurdering.KontrollsimuleringFeilet(it)
                                .left()
                        }
                    }

                    sessionFactory.withTransactionContext { tx ->
                        val gjenopptak = utbetalingService.klargjørUtbetaling(
                            utbetaling = simulertUtbetaling,
                            transactionContext = tx,
                        ).getOrElse {
                            throw IverksettTransactionException(
                                """Feil:$it ved opprettelse av utbetaling for revurdering:$revurderingId - ruller tilbake.""",
                                KunneIkkeIverksetteGjenopptakAvYtelseForRevurdering.KunneIkkeUtbetale,
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
                            tx = tx,
                        )

                        gjenopptak.sendUtbetaling().getOrElse {
                            throw IverksettTransactionException(
                                """Feil:$it ved publisering av utbetaling for revurdering:$revurderingId - ruller tilbake.""",
                                KunneIkkeIverksetteGjenopptakAvYtelseForRevurdering.KunneIkkeUtbetale,
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
                    observers.notify(StatistikkEvent.Stønadsvedtak(vedtak) { sakService.hentSak(sak.id).getOrNull()!! })
                    iverksattRevurdering
                }
            }

            else -> {
                KunneIkkeIverksetteGjenopptakAvYtelseForRevurdering.UgyldigTilstand(faktiskTilstand = revurdering::class)
                    .left()
            }
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
        ).getOrElse {
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
