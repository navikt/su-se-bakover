package no.nav.su.se.bakover.service.utbetaling

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.getOrElse
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
import no.nav.su.se.bakover.domain.oppdrag.Kvittering
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingslinje
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingsstrategi
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemmingsnøkkel
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringClient
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringFeilet
import no.nav.su.se.bakover.domain.oppdrag.utbetaling.UtbetalingPublisher
import no.nav.su.se.bakover.domain.tidslinje.Tidslinje
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

    override fun hentGjeldendeUtbetaling(sakId: UUID, forDato: LocalDate): Either<FantIkkeGjeldendeUtbetaling, Utbetalingslinje> {
        val utbetalingslinjer = hentUtbetalinger(sakId).flatMap { it.utbetalingslinjer }.filterIsInstance<Utbetalingslinje.Ny>()
        return Tidslinje(
            periode = Periode.create(
                fraOgMed = utbetalingslinjer.minOf { it.fraOgMed },
                tilOgMed = utbetalingslinjer.maxOf { it.tilOgMed }
            ),
            objekter = utbetalingslinjer,
            clock = clock,
        ).gjeldendeForDato(forDato)
            .rightIfNotNull { FantIkkeGjeldendeUtbetaling }
    }

    override fun utbetal(
        sakId: UUID,
        attestant: NavIdentBruker,
        beregning: Beregning,
        simulering: Simulering,
    ): Either<KunneIkkeUtbetale, Utbetaling.OversendtUtbetaling.UtenKvittering> {
        return simulerUtbetaling(sakId, attestant, beregning).mapLeft {
            KunneIkkeUtbetale.KunneIkkeSimulere
        }.flatMap { simulertUtbetaling ->
            if (harEndringerIUtbetalingSidenSaksbehandlersSimulering(
                    simulering,
                    simulertUtbetaling,
                )
            ) return KunneIkkeUtbetale.SimuleringHarBlittEndretSidenSaksbehandlerSimulerte.left()
            utbetal(simulertUtbetaling)
        }
    }

    override fun opphør(
        sakId: UUID,
        attestant: NavIdentBruker,
        simulering: Simulering,
        opphørsdato: LocalDate,
    ): Either<KunneIkkeUtbetale, Utbetaling.OversendtUtbetaling.UtenKvittering> {
        return simulerOpphør(
            sakId = sakId,
            saksbehandler = attestant,
            opphørsdato = opphørsdato,
        ).mapLeft {
            KunneIkkeUtbetale.KunneIkkeSimulere
        }.flatMap { simulertOpphør ->
            if (harEndringerIUtbetalingSidenSaksbehandlersSimulering(
                    simulering,
                    simulertOpphør,
                )
            ) return KunneIkkeUtbetale.SimuleringHarBlittEndretSidenSaksbehandlerSimulerte.left()
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
                clock = clock,
            ).generate(),
        )
    }

    private fun simulerUtbetaling(utbetaling: Utbetaling.UtbetalingForSimulering): Either<SimuleringFeilet, Utbetaling.SimulertUtbetaling> {
        return simuleringClient.simulerUtbetaling(utbetaling = utbetaling)
            .map { utbetaling.toSimulertUtbetaling(it) }
    }

    private fun utbetal(utbetaling: Utbetaling.SimulertUtbetaling): Either<KunneIkkeUtbetale.Protokollfeil, Utbetaling.OversendtUtbetaling.UtenKvittering> {
        return utbetalingPublisher.publish(utbetaling = utbetaling)
            .mapLeft {
                KunneIkkeUtbetale.Protokollfeil
            }.map { oppdragsmelding ->
                val oversendtUtbetaling = utbetaling.toOversendtUtbetaling(oppdragsmelding)
                utbetalingRepo.opprettUtbetaling(oversendtUtbetaling)
                oversendtUtbetaling
            }
    }

    override fun stansUtbetalinger(
        sakId: UUID,
        saksbehandler: NavIdentBruker,
    ): Either<KunneIkkeStanseUtbetalinger, Sak> {
        val sak = sakService.hentSak(sakId).getOrElse {
            return KunneIkkeStanseUtbetalinger.FantIkkeSak.left()
        }
        val utbetalingTilSimulering =
            Utbetalingsstrategi.Stans(
                sakId = sak.id,
                saksnummer = sak.saksnummer,
                fnr = sak.fnr,
                utbetalinger = sak.utbetalinger,
                behandler = saksbehandler,
                clock = clock,
            ).generate()
        return simulerUtbetaling(utbetalingTilSimulering).mapLeft {
            KunneIkkeStanseUtbetalinger.SimuleringAvStansFeilet
        }.flatMap {
            if (simulertStansHarBeløpUlikt0(it)) return KunneIkkeStanseUtbetalinger.SimulertStansHarBeløpUlikt0.left()
            utbetal(it).mapLeft {
                KunneIkkeStanseUtbetalinger.SendingAvUtebetalingTilOppdragFeilet
            }
        }.map {
            sakService.hentSak(sakId).orNull()!!
        }
    }

    private fun simulertStansHarBeløpUlikt0(simulertUtbetaling: Utbetaling.SimulertUtbetaling): Boolean {
        return if (simulertUtbetaling.simulering.nettoBeløp != 0 || simulertUtbetaling.simulering.bruttoYtelse() != 0) {
            log.error("Simulering av stansutbetaling der vi sendte inn beløp 0, nettobeløp i simulering var ${simulertUtbetaling.simulering.nettoBeløp}, bruttobeløp var:${simulertUtbetaling.simulering.bruttoYtelse()}")
            true
        } else false
    }

    override fun gjenopptaUtbetalinger(
        sakId: UUID,
        saksbehandler: NavIdentBruker,
    ): Either<KunneIkkeGjenopptaUtbetalinger, Sak> {

        val sak = sakService.hentSak(sakId).getOrElse {
            return KunneIkkeGjenopptaUtbetalinger.FantIkkeSak.left()
        }
        val utbetalingTilSimulering =
            Utbetalingsstrategi.Gjenoppta(
                sakId = sak.id,
                saksnummer = sak.saksnummer,
                fnr = sak.fnr,
                utbetalinger = sak.utbetalinger,
                behandler = saksbehandler,
                clock = clock,
            ).generate()

        return simulerUtbetaling(utbetalingTilSimulering).mapLeft {
            KunneIkkeGjenopptaUtbetalinger.SimuleringAvStartutbetalingFeilet
        }.flatMap {
            utbetal(it).mapLeft {
                KunneIkkeGjenopptaUtbetalinger.SendingAvUtebetalingTilOppdragFeilet
            }
        }.map {
            sakService.hentSak(sakId).orNull()!!
        }
    }
}
