package no.nav.su.se.bakover.service.revurdering

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.NavIdentBruker
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.log
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.common.persistence.TransactionContext
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimulerStansFeilet
import no.nav.su.se.bakover.domain.oppdrag.utbetaling.UtbetalStansFeil
import no.nav.su.se.bakover.domain.revurdering.StansAvYtelseRevurdering
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
import no.nav.su.se.bakover.domain.sak.simulerUtbetaling
import no.nav.su.se.bakover.domain.statistikk.StatistikkEvent
import no.nav.su.se.bakover.domain.statistikk.StatistikkEventObserver
import no.nav.su.se.bakover.domain.statistikk.notify
import no.nav.su.se.bakover.domain.vedtak.GjeldendeVedtaksdata
import no.nav.su.se.bakover.domain.vedtak.VedtakSomKanRevurderes
import no.nav.su.se.bakover.service.utbetaling.UtbetalingService
import no.nav.su.se.bakover.service.vedtak.VedtakService
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

class StansYtelseServiceImpl(
    private val utbetalingService: UtbetalingService,
    private val revurderingRepo: RevurderingRepo,
    private val vedtakService: VedtakService,
    private val sakService: SakService,
    private val clock: Clock,
    private val sessionFactory: SessionFactory,
) : StansYtelseService {
    private val observers: MutableList<StatistikkEventObserver> = mutableListOf()

    fun addObserver(eventObserver: StatistikkEventObserver) {
        observers.add(eventObserver)
    }

    override fun stansAvYtelseITransaksjon(
        request: StansYtelseRequest,
        transactionContext: TransactionContext,
    ): StansAvYtelseITransaksjonResponse {
        val simulertRevurdering = when (request) {
            is StansYtelseRequest.Oppdater -> {
                val sak = sakService.hentSak(
                    sakId = request.sakId,
                    sessionContext = transactionContext,
                ).getOrElse { throw KunneIkkeStanseYtelse.FantIkkeSak.exception() }

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
                            stans = null,
                            stansdato = request.fraOgMed,
                            behandler = request.saksbehandler,
                        ).getOrElse {
                            throw KunneIkkeStanseYtelse.SimuleringAvStansFeilet(it).exception()
                        }

                        eksisterende.copy(
                            periode = gjeldendeVedtaksdata.garantertSammenhengendePeriode(),
                            grunnlagsdata = gjeldendeVedtaksdata.grunnlagsdata,
                            vilkårsvurderinger = gjeldendeVedtaksdata.vilkårsvurderinger,
                            tilRevurdering = gjeldendeVedtaksdata.gjeldendeVedtakPåDato(request.fraOgMed)!!.id,
                            saksbehandler = request.saksbehandler,
                            simulering = simulertUtbetaling.simulering,
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
                ).getOrElse { throw KunneIkkeStanseYtelse.FantIkkeSak.exception() }

                if (sak.harÅpenBehandling()) {
                    throw KunneIkkeStanseYtelse.SakHarÅpenBehandling.exception()
                }

                val gjeldendeVedtaksdata = kopierGjeldendeVedtaksdata(
                    sak = sak,
                    fraOgMed = request.fraOgMed,
                ).getOrElse { throw it.exception() }

                val simulertUtbetaling = simulerStans(
                    sak = sak,
                    stans = null,
                    stansdato = request.fraOgMed,
                    behandler = request.saksbehandler,
                ).getOrElse {
                    throw KunneIkkeStanseYtelse.SimuleringAvStansFeilet(it).exception()
                }

                StansAvYtelseRevurdering.SimulertStansAvYtelse(
                    id = UUID.randomUUID(),
                    opprettet = Tidspunkt.now(clock),
                    periode = gjeldendeVedtaksdata.garantertSammenhengendePeriode(),
                    grunnlagsdata = gjeldendeVedtaksdata.grunnlagsdata,
                    vilkårsvurderinger = gjeldendeVedtaksdata.vilkårsvurderinger,
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
        revurderingId: UUID,
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
                            throw KunneIkkeIverksetteStansYtelse.KunneIkkeUtbetale(UtbetalStansFeil.KunneIkkeUtbetale(it))
                                .exception()
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
        revurderingId: UUID,
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
                val iverksattRevurdering = revurdering.iverksett(
                    Attestering.Iverksatt(
                        attestant = attestant,
                        opprettet = Tidspunkt.now(clock),
                    ),
                ).getOrElse {
                    throw KunneIkkeIverksetteStansYtelse.SimuleringIndikererFeilutbetaling.exception()
                }

                val simulertUtbetaling = simulerStans(
                    sak = sak,
                    stans = iverksattRevurdering,
                    stansdato = iverksattRevurdering.periode.fraOgMed,
                    behandler = iverksattRevurdering.attesteringer.hentSisteAttestering().attestant,
                ).getOrElse {
                    throw KunneIkkeIverksetteStansYtelse.KunneIkkeUtbetale(UtbetalStansFeil.KunneIkkeSimulere(it))
                        .exception()
                }

                val stansUtbetaling = utbetalingService.klargjørUtbetaling(
                    utbetaling = simulertUtbetaling,
                    transactionContext = transactionContext,
                ).getOrElse {
                    throw KunneIkkeIverksetteStansYtelse.KunneIkkeUtbetale(UtbetalStansFeil.KunneIkkeUtbetale(it))
                        .exception()
                }

                val vedtak = VedtakSomKanRevurderes.from(iverksattRevurdering, stansUtbetaling.utbetaling.id, clock)

                revurderingRepo.lagre(
                    revurdering = iverksattRevurdering,
                    transactionContext = transactionContext,
                )
                vedtakService.lagreITransaksjon(
                    vedtak = vedtak,
                    sessionContext = transactionContext,
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
        stans: StansAvYtelseRevurdering?,
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
            sak.simulerUtbetaling(
                utbetalingForSimulering = utbetaling,
                periode = Periode.create(
                    utbetaling.tidligsteDato(),
                    utbetaling.senesteDato(),
                ),
                simuler = utbetalingService::simulerUtbetaling,
                kontrollerMotTidligereSimulering = stans?.simulering,
                clock = clock,
            ).mapLeft {
                SimulerStansFeilet.KunneIkkeSimulere(it)
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
