package no.nav.su.se.bakover.service.revurdering

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.domain.attestering.Attestering
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.common.persistence.TransactionContext
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.oppdrag.simulering.kontrollsimuler
import no.nav.su.se.bakover.domain.oppdrag.simulering.simulerUtbetaling
import no.nav.su.se.bakover.domain.revurdering.RevurderingId
import no.nav.su.se.bakover.domain.revurdering.StansAvYtelseRevurdering
import no.nav.su.se.bakover.domain.revurdering.fromStans
import no.nav.su.se.bakover.domain.revurdering.iverksett.verifiserAtVedtaksmånedeneViRevurdererIkkeHarForandretSeg
import no.nav.su.se.bakover.domain.revurdering.repo.RevurderingRepo
import no.nav.su.se.bakover.domain.revurdering.revurderes.toVedtakSomRevurderesMånedsvis
import no.nav.su.se.bakover.domain.revurdering.stans.IverksettStansAvYtelseITransaksjonResponse
import no.nav.su.se.bakover.domain.revurdering.stans.IverksettStansAvYtelseTransactionException
import no.nav.su.se.bakover.domain.revurdering.stans.IverksettStansAvYtelseTransactionException.Companion.exception
import no.nav.su.se.bakover.domain.revurdering.stans.KunneIkkeIverksetteStansYtelse
import no.nav.su.se.bakover.domain.revurdering.stans.KunneIkkeStanseYtelse
import no.nav.su.se.bakover.domain.revurdering.stans.StansAvYtelseITransaksjonResponse
import no.nav.su.se.bakover.domain.revurdering.stans.StansAvYtelseTransactionException
import no.nav.su.se.bakover.domain.revurdering.stans.StansAvYtelseTransactionException.Companion.exception
import no.nav.su.se.bakover.domain.revurdering.stans.StansYtelseRequest
import no.nav.su.se.bakover.domain.revurdering.stans.StansYtelseService
import no.nav.su.se.bakover.domain.sak.SakService
import no.nav.su.se.bakover.domain.sak.lagUtbetalingForStans
import no.nav.su.se.bakover.domain.statistikk.StatistikkEvent
import no.nav.su.se.bakover.domain.statistikk.StatistikkEventObserver
import no.nav.su.se.bakover.domain.statistikk.notify
import no.nav.su.se.bakover.domain.vedtak.GjeldendeVedtaksdata
import no.nav.su.se.bakover.vedtak.application.VedtakService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import vedtak.domain.VedtakSomKanRevurderes
import økonomi.application.utbetaling.UtbetalingService
import økonomi.domain.simulering.SimulerStansFeilet
import økonomi.domain.simulering.Simulering
import økonomi.domain.utbetaling.Utbetaling
import java.time.Clock
import java.time.LocalDate

class StansYtelseServiceImpl(
    private val utbetalingService: UtbetalingService,
    private val revurderingRepo: RevurderingRepo,
    private val vedtakService: VedtakService,
    private val sakService: SakService,
    private val clock: Clock,
    private val sessionFactory: SessionFactory,
) : StansYtelseService {

    private val log: Logger = LoggerFactory.getLogger(this::class.java)

    private val observers: MutableList<StatistikkEventObserver> = mutableListOf()

    fun addObserver(eventObserver: StatistikkEventObserver) {
        observers.add(eventObserver)
    }

    override fun stansAvYtelseITransaksjon(
        request: StansYtelseRequest,
        transactionContext: TransactionContext,
    ): StansAvYtelseITransaksjonResponse {
        val sak = sakService.hentSak(
            sakId = request.sakId,
            sessionContext = transactionContext,
        ).getOrElse { throw KunneIkkeStanseYtelse.FantIkkeSak.exception() }

        val simulertRevurdering = when (request) {
            is StansYtelseRequest.Oppdater -> {
                val eksisterende = sak.hentRevurdering(request.revurderingId)
                    .getOrElse { throw KunneIkkeStanseYtelse.FantIkkeRevurdering.exception() }

                when (eksisterende) {
                    is StansAvYtelseRevurdering.SimulertStansAvYtelse -> {
                        val gjeldendeVedtaksdata = kopierGjeldendeVedtaksdata(
                            sak = sak,
                            fraOgMed = request.fraOgMed,
                        ).getOrElse { throw it.exception() }

                        val simulertUtbetaling = simulerStans(
                            sak = sak,
                            stansdato = request.fraOgMed,
                            behandler = request.saksbehandler,
                        ).getOrElse {
                            throw KunneIkkeStanseYtelse.SimuleringAvStansFeilet(it).exception()
                        }
                        if (simulertUtbetaling.simulering.harFeilutbetalinger()) {
                            throw KunneIkkeStanseYtelse.SimuleringInneholderFeilutbetaling.exception()
                        }

                        eksisterende.copy(
                            oppdatert = Tidspunkt.now(clock),
                            periode = gjeldendeVedtaksdata.garantertSammenhengendePeriode(),
                            grunnlagsdataOgVilkårsvurderinger = gjeldendeVedtaksdata.grunnlagsdataOgVilkårsvurderinger,
                            tilRevurdering = gjeldendeVedtaksdata.gjeldendeVedtakPåDato(request.fraOgMed)!!.id,
                            vedtakSomRevurderesMånedsvis = gjeldendeVedtaksdata.toVedtakSomRevurderesMånedsvis(),
                            saksbehandler = request.saksbehandler,
                            simulering = simulertUtbetaling.simulering,
                            revurderingsårsak = request.revurderingsårsak,
                        )
                    }

                    else -> throw KunneIkkeStanseYtelse.UgyldigTypeForOppdatering(eksisterende::class).exception()
                }
            }

            is StansYtelseRequest.Opprett -> {
                if (sak.harÅpenStansbehandling()) {
                    throw KunneIkkeStanseYtelse.FinnesÅpenStansbehandling.exception()
                }
                val gjeldendeVedtaksdata = kopierGjeldendeVedtaksdata(
                    sak = sak,
                    fraOgMed = request.fraOgMed,
                ).getOrElse { throw it.exception() }

                val simulertUtbetaling = simulerStans(
                    sak = sak,
                    stansdato = request.fraOgMed,
                    behandler = request.saksbehandler,
                ).getOrElse {
                    throw KunneIkkeStanseYtelse.SimuleringAvStansFeilet(it).exception()
                }
                if (simulertUtbetaling.simulering.harFeilutbetalinger()) {
                    throw KunneIkkeStanseYtelse.SimuleringInneholderFeilutbetaling.exception()
                }
                StansAvYtelseRevurdering.SimulertStansAvYtelse(
                    id = RevurderingId.generer(),
                    opprettet = Tidspunkt.now(clock),
                    oppdatert = Tidspunkt.now(clock),
                    periode = gjeldendeVedtaksdata.garantertSammenhengendePeriode(),
                    grunnlagsdataOgVilkårsvurderinger = gjeldendeVedtaksdata.grunnlagsdataOgVilkårsvurderinger,
                    tilRevurdering = gjeldendeVedtaksdata.gjeldendeVedtakPåDato(request.fraOgMed)!!.id,
                    vedtakSomRevurderesMånedsvis = gjeldendeVedtaksdata.toVedtakSomRevurderesMånedsvis(),
                    saksbehandler = request.saksbehandler,
                    simulering = simulertUtbetaling.simulering,
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

    override fun stansAvYtelse(
        request: StansYtelseRequest,
    ): Either<KunneIkkeStanseYtelse, StansAvYtelseRevurdering.SimulertStansAvYtelse> {
        return Either.catch {
            sessionFactory.withTransactionContext { tx ->
                stansAvYtelseITransaksjon(
                    request = request,
                    transactionContext = tx,
                ).also { response ->
                    response.sendStatistikkCallback()
                }
            }
        }.mapLeft {
            when (it) {
                is StansAvYtelseTransactionException -> {
                    it.feil
                }

                else -> {
                    KunneIkkeStanseYtelse.UkjentFeil(it.message.toString())
                }
            }
        }.map {
            it.revurdering
        }
    }

    override fun iverksettStansAvYtelse(
        revurderingId: RevurderingId,
        attestant: NavIdentBruker.Attestant,
    ): Either<KunneIkkeIverksetteStansYtelse, StansAvYtelseRevurdering.IverksattStansAvYtelse> {
        return Either.catch {
            sessionFactory.withTransactionContext { tx ->
                iverksettStansAvYtelseITransaksjon(
                    revurderingId = revurderingId,
                    attestant = attestant,
                    transactionContext = tx,
                ).also { response ->
                    response.sendUtbetalingCallback()
                        .getOrElse {
                            throw KunneIkkeIverksetteStansYtelse.KunneIkkeUtbetale.exception()
                        }
                    response.sendStatistikkCallback()
                }
            }
        }.mapLeft {
            when (it) {
                is IverksettStansAvYtelseTransactionException -> {
                    it.feil
                }

                else -> {
                    KunneIkkeIverksetteStansYtelse.UkjentFeil(it.message.toString())
                }
            }
        }.map {
            it.revurdering
        }
    }

    override fun iverksettStansAvYtelseITransaksjon(
        revurderingId: RevurderingId,
        attestant: NavIdentBruker.Attestant,
        transactionContext: TransactionContext,
    ): IverksettStansAvYtelseITransaksjonResponse {
        val sak = sakService.hentSakForRevurdering(
            revurderingId = revurderingId,
            sessionContext = transactionContext,
        )

        val revurdering = sak.hentRevurdering(revurderingId)
            .getOrElse { throw KunneIkkeIverksetteStansYtelse.FantIkkeRevurdering.exception() }

        return when (revurdering) {
            is StansAvYtelseRevurdering.SimulertStansAvYtelse -> {
                val gjeldendeVedtaksdata = kopierGjeldendeVedtaksdata(
                    sak = sak,
                    fraOgMed = revurdering.periode.fraOgMed,
                ).getOrElse { throw it.exception() }

                val stansperiodeVedIverksettelse = gjeldendeVedtaksdata.garantertSammenhengendePeriode()
                if (stansperiodeVedIverksettelse != revurdering.periode) {
                    throw KunneIkkeIverksetteStansYtelse.DetHarKommetNyeOverlappendeVedtak.exception()
                }
                if (sak.verifiserAtVedtaksmånedeneViRevurdererIkkeHarForandretSeg(
                        periode = stansperiodeVedIverksettelse,
                        eksisterendeVedtakSomRevurderesMånedsvis = revurdering.vedtakSomRevurderesMånedsvis,
                        clock = clock,
                    ).isLeft()
                ) {
                    throw KunneIkkeIverksetteStansYtelse.DetHarKommetNyeOverlappendeVedtak.exception()
                }
                val iverksattRevurdering = revurdering.iverksett(
                    Attestering.Iverksatt(
                        attestant = attestant,
                        opprettet = Tidspunkt.now(clock),
                    ),
                ).getOrElse {
                    when (it) {
                        StansAvYtelseRevurdering.KunneIkkeIverksetteStansAvYtelse.SimuleringIndikererFeilutbetaling -> {
                            throw KunneIkkeIverksetteStansYtelse.SimuleringIndikererFeilutbetaling.exception()
                        }
                    }
                }

                val simulertUtbetaling = kontrollsimulerStans(
                    sak = sak,
                    stansdato = iverksattRevurdering.periode.fraOgMed,
                    behandler = iverksattRevurdering.attesteringer.hentSisteAttestering().attestant,
                    saksbehandlersSimulering = iverksattRevurdering.simulering,
                ).getOrElse {
                    throw it.exception()
                }

                val stansUtbetaling = utbetalingService.klargjørUtbetaling(
                    utbetaling = simulertUtbetaling,
                    transactionContext = transactionContext,
                ).getOrElse {
                    // TODO jah: Klargjøringa kan ikke feile, rydd opp.
                    throw KunneIkkeIverksetteStansYtelse.KunneIkkeUtbetale.exception()
                }

                val vedtak =
                    VedtakSomKanRevurderes.fromStans(iverksattRevurdering, stansUtbetaling.utbetaling.id, clock)

                revurderingRepo.lagre(
                    revurdering = iverksattRevurdering,
                    transactionContext = transactionContext,
                )
                vedtakService.lagreITransaksjon(
                    vedtak = vedtak,
                    tx = transactionContext,
                )

                IverksettStansAvYtelseITransaksjonResponse(
                    revurdering = iverksattRevurdering,
                    vedtak = vedtak,
                    sendUtbetalingCallback = stansUtbetaling::sendUtbetaling,
                    sendStatistikkCallback = {
                        observers.notify(StatistikkEvent.Behandling.Stans.Iverksatt(vedtak))
                        // TODO jah: Vi har gjort endringer på saken underveis - endret regulering, ny utbetaling og nytt vedtak - uten at selve saken blir oppdatert underveis. Når saken returnerer en oppdatert versjon av seg selv for disse tilfellene kan vi fjerne det ekstra kallet til hentSak.
                        observers.notify(
                            StatistikkEvent.Stønadsvedtak(
                                vedtak,
                            ) { sakService.hentSak(sak.id, transactionContext).getOrNull()!! },
                        )
                    },
                )
            }

            else -> {
                throw KunneIkkeIverksetteStansYtelse.UgyldigTilstand(faktiskTilstand = revurdering::class).exception()
            }
        }
    }

    private fun simulerStans(
        sak: Sak,
        stansdato: LocalDate,
        behandler: NavIdentBruker,
    ): Either<SimulerStansFeilet, Utbetaling.SimulertUtbetaling> {
        return sak.lagUtbetalingForStans(
            stansdato = stansdato,
            behandler = behandler,
            clock = clock,
        ).mapLeft {
            SimulerStansFeilet.KunneIkkeGenerereUtbetaling(it)
        }.flatMap { utbetaling ->
            simulerUtbetaling(
                tidligereUtbetalinger = sak.utbetalinger,
                utbetalingForSimulering = utbetaling,
                simuler = utbetalingService::simulerUtbetaling,
            ).mapLeft {
                SimulerStansFeilet.KunneIkkeSimulere(it)
            }.map {
                // TODO simulering jah: Returner simuleringsresultatet til saksbehandler.
                it.simulertUtbetaling
            }
        }
    }

    private fun kontrollsimulerStans(
        sak: Sak,
        stansdato: LocalDate,
        behandler: NavIdentBruker,
        saksbehandlersSimulering: Simulering,
    ): Either<KunneIkkeIverksetteStansYtelse, Utbetaling.SimulertUtbetaling> {
        return sak.lagUtbetalingForStans(
            stansdato = stansdato,
            behandler = behandler,
            clock = clock,
        ).mapLeft {
            KunneIkkeIverksetteStansYtelse.KunneIkkeGenerereUtbetaling(it)
        }.map { utbetaling ->
            kontrollsimuler(
                utbetalingForSimulering = utbetaling,
                simuler = utbetalingService::simulerUtbetaling,
                saksbehandlersSimulering = saksbehandlersSimulering,
            ).getOrElse {
                return KunneIkkeIverksetteStansYtelse.KontrollsimuleringFeilet(it).left()
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
        ).getOrElse {
            log.error("Kunne ikke opprette revurdering for stans av ytelse, årsak: $it")
            return KunneIkkeStanseYtelse.KunneIkkeOppretteRevurdering.left()
        }.also {
            if (!it.tidslinjeForVedtakErSammenhengende()) {
                log.error("Kunne ikke opprette revurdering for stans av ytelse, årsak: tidslinje er ikke sammenhengende.")
                return KunneIkkeStanseYtelse.KunneIkkeOppretteRevurdering.left()
            }
        }.right()
    }
}
