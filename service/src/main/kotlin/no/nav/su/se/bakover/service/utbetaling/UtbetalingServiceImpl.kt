package no.nav.su.se.bakover.service.utbetaling

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.getOrElse
import arrow.core.getOrHandle
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.persistence.TransactionContext
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.oppdrag.FantIkkeGjeldendeUtbetaling
import no.nav.su.se.bakover.domain.oppdrag.KryssjekkSaksbehandlersOgAttestantsSimulering
import no.nav.su.se.bakover.domain.oppdrag.KryssjekkTidslinjerOgSimulering
import no.nav.su.se.bakover.domain.oppdrag.Kvittering
import no.nav.su.se.bakover.domain.oppdrag.SimulerUtbetalingRequest
import no.nav.su.se.bakover.domain.oppdrag.UtbetalRequest
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.UtbetalingFeilet
import no.nav.su.se.bakover.domain.oppdrag.UtbetalingslinjePåTidslinje
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingsrequest
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingsstrategi
import no.nav.su.se.bakover.domain.oppdrag.hentGjeldendeUtbetaling
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimulerUtbetalingForPeriode
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringClient
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringFeilet
import no.nav.su.se.bakover.domain.oppdrag.utbetaling.UtbetalingPublisher
import no.nav.su.se.bakover.domain.oppdrag.utbetaling.UtbetalingRepo
import no.nav.su.se.bakover.service.sak.SakService
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

internal class UtbetalingServiceImpl(
    private val utbetalingRepo: UtbetalingRepo,
    private val sakService: SakService,
    private val simuleringClient: SimuleringClient,
    private val utbetalingPublisher: UtbetalingPublisher,
    private val clock: Clock,
) : UtbetalingService {

    private val log = LoggerFactory.getLogger(this::class.java)

    override fun hentUtbetaling(utbetalingId: UUID30): Either<FantIkkeUtbetaling, Utbetaling> {
        return utbetalingRepo.hentUtbetaling(utbetalingId)?.right() ?: FantIkkeUtbetaling.left()
    }

    override fun hentUtbetalingerForSakId(sakId: UUID): List<Utbetaling> {
        return utbetalingRepo.hentUtbetalinger(sakId)
    }

    override fun oppdaterMedKvittering(
        utbetalingId: UUID30,
        kvittering: Kvittering,
    ): Either<FantIkkeUtbetaling, Utbetaling.OversendtUtbetaling.MedKvittering> {
        return utbetalingRepo.hentUtbetaling(utbetalingId)
            ?.let { utbetaling ->
                when (utbetaling) {
                    is Utbetaling.OversendtUtbetaling.MedKvittering -> {
                        log.info("Kvittering er allerede mottatt for utbetaling: ${utbetaling.id}")
                        utbetaling
                    }

                    is Utbetaling.OversendtUtbetaling.UtenKvittering -> {
                        log.info("Oppdaterer utbetaling med kvittering fra Oppdrag")
                        utbetaling.toKvittertUtbetaling(kvittering).also {
                            utbetalingRepo.oppdaterMedKvittering(it)
                        }
                    }
                }.right()
            } ?: FantIkkeUtbetaling.left()
            .also { log.warn("Fant ikke utbetaling med id: $utbetalingId") }
    }

    override fun hentGjeldendeUtbetaling(
        sakId: UUID,
        forDato: LocalDate,
    ): Either<FantIkkeGjeldendeUtbetaling, UtbetalingslinjePåTidslinje> {
        return hentUtbetalingerForSakId(sakId).hentGjeldendeUtbetaling(
            forDato,
            clock,
        )
    }

    override fun verifiserOgSimulerUtbetaling(
        request: UtbetalRequest.NyUtbetaling,
    ): Either<UtbetalingFeilet, Utbetaling.SimulertUtbetaling> {
        return simulerUtbetaling(request).mapLeft {
            UtbetalingFeilet.KunneIkkeSimulere(it)
        }.flatMap { simulertUtbetaling ->
            KryssjekkSaksbehandlersOgAttestantsSimulering(
                saksbehandlersSimulering = request.simulering,
                attestantsSimulering = simulertUtbetaling,
            ).sjekk().getOrHandle {
                return UtbetalingFeilet.SimuleringHarBlittEndretSidenSaksbehandlerSimulerte(it).left()
            }
            simulertUtbetaling.right()
        }
    }

    override fun verifiserOgSimulerOpphør(
        request: UtbetalRequest.Opphør,
    ): Either<UtbetalingFeilet, Utbetaling.SimulertUtbetaling> {
        return simulerOpphør(request).mapLeft {
            UtbetalingFeilet.KunneIkkeSimulere(it)
        }.flatMap { simulertOpphør ->
            KryssjekkSaksbehandlersOgAttestantsSimulering(
                saksbehandlersSimulering = request.simulering,
                attestantsSimulering = simulertOpphør,
            ).sjekk().getOrHandle {
                return UtbetalingFeilet.SimuleringHarBlittEndretSidenSaksbehandlerSimulerte(it).left()
            }
            simulertOpphør.right()
        }
    }

    override fun simulerOpphør(
        request: SimulerUtbetalingRequest.OpphørRequest,
    ): Either<SimuleringFeilet, Utbetaling.SimulertUtbetaling> {
        val sak: Sak = sakService.hentSak(request.sakId).orNull()!!

        val utbetaling = Utbetalingsstrategi.Opphør(
            sakId = sak.id,
            saksnummer = sak.saksnummer,
            fnr = sak.fnr,
            eksisterendeUtbetalinger = sak.utbetalinger,
            behandler = request.saksbehandler,
            periode = request.opphørsperiode,
            clock = clock,
            sakstype = sak.type,
        ).generate()

        val simuleringsperiode = request.opphørsperiode

        KryssjekkTidslinjerOgSimulering.sjekkNyEllerOpphør(
            underArbeidEndringsperiode = simuleringsperiode,
            underArbeid = utbetaling,
            eksisterende = sak.utbetalinger,
            simuler = this::simulerUtbetaling,
            clock = clock,
        )

        return simulerUtbetaling(
            request = SimulerUtbetalingForPeriode(
                utbetaling = utbetaling,
                simuleringsperiode = simuleringsperiode,
            ),
        )
    }

    override fun simulerUtbetaling(
        request: SimulerUtbetalingRequest.NyUtbetalingRequest,
    ): Either<SimuleringFeilet, Utbetaling.SimulertUtbetaling> {
        val sak: Sak = sakService.hentSak(request.sakId).orNull()!!

        fun lagUtbetalingAlder(request: SimulerUtbetalingRequest.NyUtbetaling.Alder): Utbetaling.UtbetalingForSimulering {
            return Utbetalingsstrategi.NyAldersUtbetaling(
                sakId = sak.id,
                saksnummer = sak.saksnummer,
                fnr = sak.fnr,
                eksisterendeUtbetalinger = sak.utbetalinger,
                behandler = request.saksbehandler,
                beregning = request.beregning,
                clock = clock,
                kjøreplan = request.utbetalingsinstruksjonForEtterbetaling,
                sakstype = sak.type,
            ).generate()
        }

        fun lagUtbetalingUføre(request: SimulerUtbetalingRequest.NyUtbetaling.Uføre): Utbetaling.UtbetalingForSimulering {
            return Utbetalingsstrategi.NyUføreUtbetaling(
                sakId = sak.id,
                saksnummer = sak.saksnummer,
                fnr = sak.fnr,
                eksisterendeUtbetalinger = sak.utbetalinger,
                behandler = request.saksbehandler,
                beregning = request.beregning,
                uføregrunnlag = request.uføregrunnlag,
                clock = clock,
                kjøreplan = request.utbetalingsinstruksjonForEtterbetaling,
                sakstype = sak.type,
            ).generate()
        }

        val utbetaling = when (request) {
            is SimulerUtbetalingRequest.NyUtbetaling.Alder -> {
                lagUtbetalingAlder(request)
            }

            is SimulerUtbetalingRequest.NyUtbetaling.Uføre -> {
                lagUtbetalingUføre(request)
            }

            is UtbetalRequest.NyUtbetaling -> {
                when (val inner = request.request) {
                    is SimulerUtbetalingRequest.NyUtbetaling.Alder -> lagUtbetalingAlder(inner)
                    is SimulerUtbetalingRequest.NyUtbetaling.Uføre -> lagUtbetalingUføre(inner)
                }
            }
        }

        val simuleringsperiode = request.beregning.periode

        KryssjekkTidslinjerOgSimulering.sjekkNyEllerOpphør(
            underArbeidEndringsperiode = simuleringsperiode,
            underArbeid = utbetaling,
            eksisterende = sak.utbetalinger,
            simuler = this::simulerUtbetaling,
            clock = clock,
        )

        return simulerUtbetaling(
            request = SimulerUtbetalingForPeriode(
                utbetaling = utbetaling,
                simuleringsperiode = simuleringsperiode,
            ),
        )
    }

    private fun simulerUtbetaling(
        request: no.nav.su.se.bakover.domain.oppdrag.simulering.SimulerUtbetalingRequest,
    ): Either<SimuleringFeilet, Utbetaling.SimulertUtbetaling> {
        return simuleringClient.simulerUtbetaling(request = request)
            .map { request.utbetaling.toSimulertUtbetaling(it) }
    }

    override fun lagreUtbetaling(
        utbetaling: Utbetaling.SimulertUtbetaling,
        transactionContext: TransactionContext?,
    ): Utbetaling.OversendtUtbetaling.UtenKvittering {
        val oppdragsmelding = utbetalingPublisher.generateRequest(utbetaling)
        val oversendtUtbetaling = utbetaling.toOversendtUtbetaling(oppdragsmelding)
        val context = transactionContext ?: utbetalingRepo.defaultTransactionContext()
        utbetalingRepo.opprettUtbetaling(
            oversendtUtbetaling,
            context,
        )
        return oversendtUtbetaling
    }

    override fun publiserUtbetaling(
        utbetaling: Utbetaling.SimulertUtbetaling,
    ): Either<UtbetalingFeilet, Utbetalingsrequest> =
        utbetalingPublisher.publish(utbetaling).mapLeft {
            UtbetalingFeilet.Protokollfeil
        }

    private fun utbetal(utbetaling: Utbetaling.SimulertUtbetaling): Either<UtbetalingFeilet.Protokollfeil, Utbetaling.OversendtUtbetaling.UtenKvittering> {
        return utbetalingPublisher.publish(utbetaling = utbetaling)
            .mapLeft {
                UtbetalingFeilet.Protokollfeil
            }.map { oppdragsmelding ->
                val oversendtUtbetaling = utbetaling.toOversendtUtbetaling(oppdragsmelding)
                utbetalingRepo.opprettUtbetaling(oversendtUtbetaling)
                oversendtUtbetaling
            }
    }

    override fun simulerStans(
        request: SimulerUtbetalingRequest.StansRequest,
    ): Either<SimulerStansFeilet, Utbetaling.SimulertUtbetaling> {
        val sak: Sak = sakService.hentSak(request.sakId).orNull()!!

        val utbetaling = Utbetalingsstrategi.Stans(
            sakId = sak.id,
            saksnummer = sak.saksnummer,
            fnr = sak.fnr,
            eksisterendeUtbetalinger = sak.utbetalinger,
            behandler = request.saksbehandler,
            stansDato = request.stansdato,
            clock = clock,
            sakstype = sak.type,
        ).generer()
            .getOrHandle {
                return SimulerStansFeilet.KunneIkkeGenerereUtbetaling(it).left()
            }

        val simuleringsperiode = Periode.create(
            fraOgMed = utbetaling.tidligsteDato(),
            tilOgMed = utbetaling.senesteDato(),
        )

        return simulerUtbetaling(
            request = SimulerUtbetalingForPeriode(
                utbetaling = utbetaling,
                simuleringsperiode = simuleringsperiode,
            ),
        ).map {
            KryssjekkTidslinjerOgSimulering.sjekkStans(underArbeid = it)
            it
        }.mapLeft {
            SimulerStansFeilet.KunneIkkeSimulere(it)
        }
    }

    override fun stansUtbetalinger(
        request: UtbetalRequest.Stans,
    ): Either<UtbetalStansFeil, Utbetaling.OversendtUtbetaling.UtenKvittering> {
        return simulerStans(request = request)
            .mapLeft {
                UtbetalStansFeil.KunneIkkeSimulere(it)
            }.flatMap { simulertStans ->
                KryssjekkSaksbehandlersOgAttestantsSimulering(
                    saksbehandlersSimulering = request.simulering,
                    attestantsSimulering = simulertStans,
                ).sjekk().getOrHandle {
                    return UtbetalStansFeil.KunneIkkeUtbetale(
                        UtbetalingFeilet.SimuleringHarBlittEndretSidenSaksbehandlerSimulerte(
                            it,
                        ),
                    ).left()
                }
                utbetal(simulertStans)
                    .mapLeft {
                        UtbetalStansFeil.KunneIkkeUtbetale(it)
                    }
            }
    }

    override fun simulerGjenopptak(
        request: SimulerUtbetalingRequest.GjenopptakRequest,
    ): Either<SimulerGjenopptakFeil, Utbetaling.SimulertUtbetaling> {
        val utbetaling = Utbetalingsstrategi.Gjenoppta(
            sakId = request.sakId,
            saksnummer = request.sak.saksnummer,
            fnr = request.sak.fnr,
            eksisterendeUtbetalinger = request.sak.utbetalinger,
            behandler = request.saksbehandler,
            clock = clock,
            sakstype = request.sak.type,
        ).generer()
            .getOrHandle { return SimulerGjenopptakFeil.KunneIkkeGenerereUtbetaling(it).left() }

        val simuleringsperiode = Periode.create(
            fraOgMed = utbetaling.tidligsteDato(),
            tilOgMed = utbetaling.senesteDato(),
        )

        return simulerUtbetaling(
            request = SimulerUtbetalingForPeriode(
                utbetaling = utbetaling,
                simuleringsperiode = simuleringsperiode,
            ),
        ).map {
            KryssjekkTidslinjerOgSimulering.sjekkGjenopptak(
                underArbeid = it,
                eksisterende = request.sak.utbetalinger,
                clock = clock,
            )
            it
        }.mapLeft {
            SimulerGjenopptakFeil.KunneIkkeSimulere(it)
        }
    }

    override fun gjenopptaUtbetalinger(
        request: UtbetalRequest.Gjenopptak,
    ): Either<UtbetalGjenopptakFeil, Utbetaling.OversendtUtbetaling.UtenKvittering> {
        val sak = sakService.hentSak(request.sakId).getOrElse {
            return UtbetalGjenopptakFeil.KunneIkkeUtbetale(UtbetalingFeilet.FantIkkeSak).left()
        }

        return simulerGjenopptak(
            request = SimulerUtbetalingRequest.Gjenopptak(
                saksbehandler = request.saksbehandler,
                sak = sak,
            ),
        ).mapLeft {
            UtbetalGjenopptakFeil.KunneIkkeSimulere(it)
        }.flatMap { simulertGjenopptak ->
            KryssjekkSaksbehandlersOgAttestantsSimulering(
                saksbehandlersSimulering = request.simulering,
                attestantsSimulering = simulertGjenopptak,
            ).sjekk().getOrHandle {
                return UtbetalGjenopptakFeil.KunneIkkeUtbetale(
                    UtbetalingFeilet.SimuleringHarBlittEndretSidenSaksbehandlerSimulerte(
                        it,
                    ),
                ).left()
            }

            utbetal(simulertGjenopptak)
                .mapLeft {
                    UtbetalGjenopptakFeil.KunneIkkeUtbetale(it)
                }
        }
    }
}
