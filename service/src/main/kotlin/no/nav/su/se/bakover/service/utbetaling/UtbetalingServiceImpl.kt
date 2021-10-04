package no.nav.su.se.bakover.service.utbetaling

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.getOrElse
import arrow.core.getOrHandle
import arrow.core.left
import arrow.core.right
import arrow.core.rightIfNotNull
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.sikkerLogg
import no.nav.su.se.bakover.database.utbetaling.UtbetalingRepo
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.oppdrag.Kvittering
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.UtbetalingFeilet
import no.nav.su.se.bakover.domain.oppdrag.UtbetalingslinjePåTidslinje
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingsstrategi
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemmingsnøkkel
import no.nav.su.se.bakover.domain.oppdrag.simulering.KontrollerSimulering
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringClient
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringFeilet
import no.nav.su.se.bakover.domain.oppdrag.utbetaling.UtbetalingPublisher
import no.nav.su.se.bakover.domain.tidslinje.TidslinjeForUtbetalinger
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

    override fun hentUtbetalinger(sakId: UUID): List<Utbetaling> {
        return utbetalingRepo.hentUtbetalinger(sakId)
    }

    override fun oppdaterMedKvittering(
        avstemmingsnøkkel: Avstemmingsnøkkel,
        kvittering: Kvittering,
    ): Either<FantIkkeUtbetaling, Utbetaling.OversendtUtbetaling.MedKvittering> {
        return utbetalingRepo.hentUtbetaling(avstemmingsnøkkel)
            ?.let {
                when (it) {
                    is Utbetaling.OversendtUtbetaling.MedKvittering -> {
                        log.info("Kvittering er allerede mottatt for utbetaling: ${it.id}")
                        it
                    }
                    is Utbetaling.OversendtUtbetaling.UtenKvittering -> {
                        it.toKvittertUtbetaling(kvittering).also {
                            utbetalingRepo.oppdaterMedKvittering(it)
                        }
                    }
                }.right()
            } ?: FantIkkeUtbetaling.left()
    }

    override fun hentGjeldendeUtbetaling(
        sakId: UUID,
        forDato: LocalDate,
    ): Either<FantIkkeGjeldendeUtbetaling, UtbetalingslinjePåTidslinje> {
        val utbetalingslinjer = hentUtbetalinger(sakId).flatMap { it.utbetalingslinjer }

        return TidslinjeForUtbetalinger(
            periode = Periode.create(
                fraOgMed = utbetalingslinjer.minOf { it.fraOgMed },
                tilOgMed = utbetalingslinjer.maxOf { it.tilOgMed },
            ),
            utbetalingslinjer = utbetalingslinjer,
            clock = clock,
        ).gjeldendeForDato(forDato).rightIfNotNull { FantIkkeGjeldendeUtbetaling }
    }

    override fun utbetal(
        sakId: UUID,
        attestant: NavIdentBruker,
        beregning: Beregning,
        simulering: Simulering,
        uføregrunnlag: List<Grunnlag.Uføregrunnlag>,
    ): Either<UtbetalingFeilet, Utbetaling.OversendtUtbetaling.UtenKvittering> {
        return simulerUtbetaling(
            sakId = sakId,
            saksbehandler = attestant,
            beregning = beregning,
            uføregrunnlag = uføregrunnlag,
        ).mapLeft {
            UtbetalingFeilet.KunneIkkeSimulere(it)
        }.flatMap { simulertUtbetaling ->
            if (harEndringerIUtbetalingSidenSaksbehandlersSimulering(
                    simulering,
                    simulertUtbetaling,
                )
            ) return UtbetalingFeilet.SimuleringHarBlittEndretSidenSaksbehandlerSimulerte.left()
            utbetal(simulertUtbetaling)
        }
    }

    override fun opphør(
        sakId: UUID,
        attestant: NavIdentBruker,
        simulering: Simulering,
        opphørsdato: LocalDate,
    ): Either<UtbetalingFeilet, Utbetaling.OversendtUtbetaling.UtenKvittering> {
        return simulerOpphør(
            sakId = sakId,
            saksbehandler = attestant,
            opphørsdato = opphørsdato,
        ).mapLeft {
            UtbetalingFeilet.KunneIkkeSimulere(it)
        }.flatMap { simulertOpphør ->
            if (harEndringerIUtbetalingSidenSaksbehandlersSimulering(
                    simulering,
                    simulertOpphør,
                )
            ) return UtbetalingFeilet.SimuleringHarBlittEndretSidenSaksbehandlerSimulerte.left()
            utbetal(simulertOpphør)
        }
    }

    override fun simulerOpphør(
        sakId: UUID,
        saksbehandler: NavIdentBruker,
        opphørsdato: LocalDate,
    ): Either<SimuleringFeilet, Utbetaling.SimulertUtbetaling> {
        val sak: Sak = sakService.hentSak(sakId).orNull()!!
        return simulerUtbetaling(
            Utbetalingsstrategi.Opphør(
                sakId = sak.id,
                saksnummer = sak.saksnummer,
                fnr = sak.fnr,
                utbetalinger = sak.utbetalinger,
                behandler = saksbehandler,
                opphørsDato = opphørsdato,
                clock = clock,
            ).generate(),
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
        sakId: UUID,
        saksbehandler: NavIdentBruker,
        beregning: Beregning,
        uføregrunnlag: List<Grunnlag.Uføregrunnlag>,
    ): Either<SimuleringFeilet, Utbetaling.SimulertUtbetaling> {
        val sak: Sak = sakService.hentSak(sakId).orNull()!!
        return simulerUtbetaling(
            Utbetalingsstrategi.Ny(
                sakId = sak.id,
                saksnummer = sak.saksnummer,
                fnr = sak.fnr,
                utbetalinger = sak.utbetalinger,
                behandler = saksbehandler,
                beregning = beregning,
                uføregrunnlag = uføregrunnlag,
                clock = clock,
            ).generate(),
        )
    }

    private fun simulerUtbetaling(utbetaling: Utbetaling.UtbetalingForSimulering): Either<SimuleringFeilet, Utbetaling.SimulertUtbetaling> {
        return simuleringClient.simulerUtbetaling(utbetaling = utbetaling)
            .map { utbetaling.toSimulertUtbetaling(it) }
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
        sakId: UUID,
        saksbehandler: NavIdentBruker,
        stansDato: LocalDate,
    ): Either<SimulerStansFeilet, Utbetaling.SimulertUtbetaling> {
        val sak: Sak = sakService.hentSak(sakId).orNull()!!
        return Utbetalingsstrategi.Stans(
            sakId = sak.id,
            saksnummer = sak.saksnummer,
            fnr = sak.fnr,
            utbetalinger = sak.utbetalinger,
            behandler = saksbehandler,
            stansDato = stansDato,
            clock = clock,
        ).generer()
            .mapLeft {
                SimulerStansFeilet.KunneIkkeGenerereUtbetaling(it)
            }.flatMap { utbetalingForSimulering ->
                simulerUtbetaling(utbetalingForSimulering)
                    .mapLeft {
                        SimulerStansFeilet.KunneIkkeSimulere(it)
                    }
            }
    }

    override fun stansUtbetalinger(
        sakId: UUID,
        attestant: NavIdentBruker,
        simulering: Simulering,
        stansDato: LocalDate,
    ): Either<UtbetalStansFeil, Utbetaling.OversendtUtbetaling.UtenKvittering> {
        val sak: Sak = sakService.hentSak(sakId).orNull()!!

        return simulerStans(
            sakId = sakId,
            saksbehandler = attestant,
            stansDato = stansDato,
        ).mapLeft {
            UtbetalStansFeil.KunneIkkeSimulere(it)
        }.flatMap { simulertStans ->
            if (harEndringerIUtbetalingSidenSaksbehandlersSimulering(
                    saksbehandlersSimulering = simulering,
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
        sakId: UUID,
        saksbehandler: NavIdentBruker,
    ): Either<SimulerGjenopptakFeil, Utbetaling.SimulertUtbetaling> {
        val sak: Sak = sakService.hentSak(sakId).orNull()!!

        return Utbetalingsstrategi.Gjenoppta(
            sakId = sak.id,
            saksnummer = sak.saksnummer,
            fnr = sak.fnr,
            utbetalinger = sak.utbetalinger,
            behandler = saksbehandler,
            clock = clock,
        ).generer()
            .mapLeft {
                SimulerGjenopptakFeil.KunneIkkeGenerereUtbetaling(it)
            }.flatMap { utbetalingForSimulering ->
                simulerUtbetaling(utbetalingForSimulering)
                    .mapLeft {
                        SimulerGjenopptakFeil.KunneIkkeSimulere(it)
                    }
            }
    }

    override fun gjenopptaUtbetalinger(
        sakId: UUID,
        attestant: NavIdentBruker,
        simulering: Simulering,
    ): Either<UtbetalGjenopptakFeil, Utbetaling.OversendtUtbetaling.UtenKvittering> {
        val sak = sakService.hentSak(sakId).getOrElse {
            return UtbetalGjenopptakFeil.KunneIkkeUtbetale(UtbetalingFeilet.FantIkkeSak).left()
        }
        return simulerGjenopptak(
            sakId = sakId,
            saksbehandler = attestant,
        ).mapLeft {
            UtbetalGjenopptakFeil.KunneIkkeSimulere(it)
        }.flatMap { simulertGjenopptak ->
            if (harEndringerIUtbetalingSidenSaksbehandlersSimulering(
                    saksbehandlersSimulering = simulering,
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
