package no.nav.su.se.bakover.service.utbetaling

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.getOrElse
import arrow.core.getOrHandle
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.persistence.TransactionContext
import no.nav.su.se.bakover.common.sikkerLogg
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.oppdrag.FantIkkeGjeldendeUtbetaling
import no.nav.su.se.bakover.domain.oppdrag.Kvittering
import no.nav.su.se.bakover.domain.oppdrag.SimulerUtbetalingRequest
import no.nav.su.se.bakover.domain.oppdrag.UtbetalRequest
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.UtbetalingFeilet
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingslinje
import no.nav.su.se.bakover.domain.oppdrag.UtbetalingslinjePåTidslinje
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingsrequest
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingsstrategi
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemmingsnøkkel
import no.nav.su.se.bakover.domain.oppdrag.hentGjeldendeUtbetaling
import no.nav.su.se.bakover.domain.oppdrag.simulering.KontrollerSimulering
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimulerUtbetalingForPeriode
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
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
        avstemmingsnøkkel: Avstemmingsnøkkel,
        kvittering: Kvittering,
    ): Either<FantIkkeUtbetaling, Utbetaling.OversendtUtbetaling.MedKvittering> {
        return utbetalingRepo.hentUtbetaling(avstemmingsnøkkel)
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
            .also { log.warn("Fant ikke utbetaling for avstemmingsnøkkel $avstemmingsnøkkel") }
    }

    override fun hentGjeldendeUtbetaling(
        sakId: UUID,
        forDato: LocalDate,
    ): Either<FantIkkeGjeldendeUtbetaling, UtbetalingslinjePåTidslinje> {
        return hentUtbetalingerForSakId(sakId).hentGjeldendeUtbetaling(forDato, clock)
    }

    override fun verifiserOgSimulerUtbetaling(
        request: UtbetalRequest.NyUtbetaling,
    ): Either<UtbetalingFeilet, Utbetaling.SimulertUtbetaling> {
        return simulerUtbetaling(request).mapLeft {
            UtbetalingFeilet.KunneIkkeSimulere(it)
        }.flatMap { simulertUtbetaling ->
            if (harEndringerIUtbetalingSidenSaksbehandlersSimulering(
                    saksbehandlersSimulering = request.simulering,
                    attestantsSimulering = simulertUtbetaling,
                )
            ) return UtbetalingFeilet.SimuleringHarBlittEndretSidenSaksbehandlerSimulerte.left()
            simulertUtbetaling.right()
        }
    }

    override fun verifiserOgSimulerOpphør(
        request: UtbetalRequest.Opphør,
    ): Either<UtbetalingFeilet, Utbetaling.SimulertUtbetaling> {
        return simulerOpphør(request).mapLeft {
            UtbetalingFeilet.KunneIkkeSimulere(it)
        }.flatMap { simulertOpphør ->
            if (harEndringerIUtbetalingSidenSaksbehandlersSimulering(
                    saksbehandlersSimulering = request.simulering,
                    attestantsSimulering = simulertOpphør,
                )
            ) return UtbetalingFeilet.SimuleringHarBlittEndretSidenSaksbehandlerSimulerte.left()
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
            utbetalinger = sak.utbetalinger,
            behandler = request.saksbehandler,
            opphørsDato = request.opphørsdato,
            clock = clock,
            sakstype = sak.type,
        ).generate()

        val simuleringsperiode = Periode.create(
            fraOgMed = request.opphørsdato,
            tilOgMed = utbetaling.senesteDato(),
        )

        return simulerUtbetaling(
            request = SimulerUtbetalingForPeriode(
                utbetaling = utbetaling,
                simuleringsperiode = simuleringsperiode,
            ),
        )
    }

    /**
     * Det kan ha gått en stund siden saksbehandler simulerte utbetalingen.
     * Vi ønsker å sjekke at simuleringen ved utbetalingsøyeblikket er lik som den vi fremviste saksbehandler og senere, attestant.
     *
     * TODO: Må teste i preprod om denne sjekken er adekvat.
     */
    private fun harEndringerIUtbetalingSidenSaksbehandlersSimulering(
        saksbehandlersSimulering: Simulering,
        attestantsSimulering: Utbetaling.SimulertUtbetaling,
    ): Boolean {
        return if (saksbehandlersSimulering != attestantsSimulering.simulering) {
            log.error("Utbetaling kunne ikke gjennomføres, kontrollsimulering er ulik saksbehandlers simulering. Se sikkerlogg for detaljer.")
            sikkerLogg.error(
                "Utbetaling kunne ikke gjennomføres, kontrollsimulering: {}, er ulik saksbehandlers simulering: {}",
                objectMapper.writeValueAsString(attestantsSimulering.simulering),
                objectMapper.writeValueAsString(saksbehandlersSimulering),
            )
            true
        } else false
    }

    override fun simulerUtbetaling(
        request: SimulerUtbetalingRequest.NyUtbetalingRequest,
    ): Either<SimuleringFeilet, Utbetaling.SimulertUtbetaling> {
        val sak: Sak = sakService.hentSak(request.sakId).orNull()!!

        val utbetaling = Utbetalingsstrategi.Ny(
            sakId = sak.id,
            saksnummer = sak.saksnummer,
            fnr = sak.fnr,
            utbetalinger = sak.utbetalinger,
            behandler = request.saksbehandler,
            beregning = request.beregning,
            uføregrunnlag = request.uføregrunnlag,
            clock = clock,
            kjøreplan = request.utbetalingsinstruksjonForEtterbetaling,
            sakstype = sak.type,
        ).generate()

        val simuleringsperiode = request.beregning.periode

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
        utbetalingRepo.opprettUtbetaling(oversendtUtbetaling, context)
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
            utbetalinger = sak.utbetalinger,
            behandler = request.saksbehandler,
            stansDato = request.stansdato,
            clock = clock,
            sakstype = sak.type,
        ).generer()
            .getOrHandle {
                return SimulerStansFeilet.KunneIkkeGenerereUtbetaling(it).left()
            }

        val simuleringsperiode = Periode.create(
            fraOgMed = request.stansdato,
            tilOgMed = utbetaling.senesteDato(),
        )

        return simulerUtbetaling(
            request = SimulerUtbetalingForPeriode(
                utbetaling = utbetaling,
                simuleringsperiode = simuleringsperiode,
            ),
        ).mapLeft {
            SimulerStansFeilet.KunneIkkeSimulere(it)
        }
    }

    override fun stansUtbetalinger(
        request: UtbetalRequest.Stans,
    ): Either<UtbetalStansFeil, Utbetaling.OversendtUtbetaling.UtenKvittering> {
        val sak: Sak = sakService.hentSak(request.sakId).orNull()!!

        return simulerStans(request = request)
            .mapLeft {
                UtbetalStansFeil.KunneIkkeSimulere(it)
            }.flatMap { simulertStans ->
                if (harEndringerIUtbetalingSidenSaksbehandlersSimulering(
                        saksbehandlersSimulering = request.simulering,
                        attestantsSimulering = simulertStans,
                    )
                ) return UtbetalStansFeil.KunneIkkeUtbetale(UtbetalingFeilet.SimuleringHarBlittEndretSidenSaksbehandlerSimulerte)
                    .left()

                KontrollerSimulering(
                    simulertUtbetaling = simulertStans,
                    eksisterendeUtbetalinger = sak.utbetalinger,
                    clock = clock,
                ).resultat.getOrHandle {
                    return UtbetalStansFeil.KunneIkkeUtbetale(UtbetalingFeilet.KontrollAvSimuleringFeilet).left()
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
            utbetalinger = request.sak.utbetalinger,
            behandler = request.saksbehandler,
            clock = clock,
            sakstype = request.sak.type,
        ).generer()
            .getOrHandle { return SimulerGjenopptakFeil.KunneIkkeGenerereUtbetaling(it).left() }

        // TODO løse dette på en annen måte? Litt cheaky, men bør være safe (kan f.eks refaktorere modellen til at endringer kun har 1 linje.
        val reaktiveringslinje = utbetaling.utbetalingslinjer
            .filterIsInstance<Utbetalingslinje.Endring.Reaktivering>()
            .single()

        val simuleringsperiode = Periode.create(
            fraOgMed = reaktiveringslinje.virkningstidspunkt,
            tilOgMed = reaktiveringslinje.tilOgMed,
        )

        return simulerUtbetaling(
            request = SimulerUtbetalingForPeriode(
                utbetaling = utbetaling,
                simuleringsperiode = simuleringsperiode,
            ),
        ).mapLeft {
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
            if (harEndringerIUtbetalingSidenSaksbehandlersSimulering(
                    saksbehandlersSimulering = request.simulering,
                    attestantsSimulering = simulertGjenopptak,
                )
            ) return UtbetalGjenopptakFeil.KunneIkkeUtbetale(UtbetalingFeilet.SimuleringHarBlittEndretSidenSaksbehandlerSimulerte)
                .left()

            KontrollerSimulering(
                simulertUtbetaling = simulertGjenopptak,
                eksisterendeUtbetalinger = sak.utbetalinger,
                clock = clock,
            ).resultat.getOrHandle {
                return UtbetalGjenopptakFeil.KunneIkkeUtbetale(UtbetalingFeilet.KontrollAvSimuleringFeilet).left()
            }

            utbetal(simulertGjenopptak)
                .mapLeft {
                    UtbetalGjenopptakFeil.KunneIkkeUtbetale(it)
                }
        }
    }
}
