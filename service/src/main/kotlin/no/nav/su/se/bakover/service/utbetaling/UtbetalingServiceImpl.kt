package no.nav.su.se.bakover.service.utbetaling

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.database.utbetaling.UtbetalingRepo
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.beregning.beregning.IBeregning
import no.nav.su.se.bakover.domain.oppdrag.Kvittering
import no.nav.su.se.bakover.domain.oppdrag.Oppdrag
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemmingsnøkkel
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringClient
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringFeilet
import no.nav.su.se.bakover.domain.oppdrag.utbetaling.UtbetalingPublisher
import no.nav.su.se.bakover.service.sak.SakService
import org.slf4j.LoggerFactory
import java.time.Clock
import java.util.UUID

internal class UtbetalingServiceImpl(
    private val utbetalingRepo: UtbetalingRepo,
    private val sakService: SakService,
    private val simuleringClient: SimuleringClient,
    private val utbetalingPublisher: UtbetalingPublisher,
    private val clock: Clock = Clock.systemUTC()
) : UtbetalingService {
    private val log = LoggerFactory.getLogger(this::class.java)

    override fun hentUtbetaling(utbetalingId: UUID30): Either<FantIkkeUtbetaling, Utbetaling> {
        return utbetalingRepo.hentUtbetaling(utbetalingId)?.right() ?: FantIkkeUtbetaling.left()
    }

    override fun oppdaterMedKvittering(
        avstemmingsnøkkel: Avstemmingsnøkkel,
        kvittering: Kvittering
    ): Either<FantIkkeUtbetaling, Utbetaling> {
        return utbetalingRepo.hentUtbetaling(avstemmingsnøkkel)
            ?.let {
                if (it is Utbetaling.OversendtUtbetaling.MedKvittering) {
                    log.info("Kvittering er allerede mottatt for utbetaling: ${it.id}")
                    it
                } else {
                    utbetalingRepo.oppdaterMedKvittering(it.id, kvittering)
                }.right()
            } ?: FantIkkeUtbetaling.left()
    }

    private fun lagUtbetaling(
        sakId: UUID,
        strategy: Oppdrag.UtbetalingStrategy
    ): Utbetaling.UtbetalingForSimulering {
        val sak = sakService.hentSak(sakId).orNull()!!
        return sak.oppdrag.genererUtbetaling(strategy, sak.fnr)
    }

    override fun utbetal(
        sakId: UUID,
        attestant: NavIdentBruker,
        beregning: IBeregning,
        simulering: Simulering
    ): Either<KunneIkkeUtbetale, Utbetaling.OversendtUtbetaling.UtenKvittering> {
        return simulerUtbetaling(sakId, attestant, beregning).mapLeft {
            KunneIkkeUtbetale.KunneIkkeSimulere
        }.flatMap { simulertUtbetaling ->
            if (harEndringerIUtbetalingSidenSaksbehandlersSimulering(
                    simulering,
                    simulertUtbetaling
                )
            ) return KunneIkkeUtbetale.SimuleringHarBlittEndretSidenSaksbehandlerSimulerte.left()
            utbetal(simulertUtbetaling)
        }
    }

    /**
     * Det kan ha gått en stund siden saksbehandler simulerte utbetalingen.
     * Vi ønsker å sjekke at simuleringen ved utbetalingsøyeblikket er lik som den vi fremviste saksbehandler og senere, attestant.
     *
     * TODO: Må teste i preprod om denne sjekken er adekvat.
     */
    private fun harEndringerIUtbetalingSidenSaksbehandlersSimulering(
        saksbehandlersSimulering: Simulering,
        attestantsSimulering: Utbetaling.SimulertUtbetaling
    ): Boolean {
        return if (saksbehandlersSimulering != attestantsSimulering.simulering) {
            log.error(
                "Kunne ikke utbetale siden saksbehandlers simulering ikke matcher den verifiserende simuleringa. Saksbehandlers simulering: {}, Verifiserende simulering: {}",
                saksbehandlersSimulering,
                attestantsSimulering.simulering
            )
            true
        } else false
    }

    override fun simulerUtbetaling(
        sakId: UUID,
        saksbehandler: NavIdentBruker,
        beregning: IBeregning
    ): Either<SimuleringFeilet, Utbetaling.SimulertUtbetaling> {
        return simulerUtbetaling(
            lagUtbetaling(
                sakId = sakId,
                strategy = Oppdrag.UtbetalingStrategy.Ny(
                    behandler = saksbehandler,
                    beregning = beregning
                ),
            )
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
        saksbehandler: NavIdentBruker
    ): Either<KunneIkkeStanseUtbetalinger, Sak> {
        val sak = sakService.hentSak(sakId).getOrElse {
            return KunneIkkeStanseUtbetalinger.FantIkkeSak.left()
        }
        val utbetalingTilSimulering =
            sak.oppdrag.genererUtbetaling(Oppdrag.UtbetalingStrategy.Stans(saksbehandler), sak.fnr)
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
        return if (simulertUtbetaling.simulering.nettoBeløp != 0.0 || simulertUtbetaling.simulering.bruttoYtelse() != 0.0) {
            log.error("Simulering av stansutbetaling der vi sendte inn beløp 0, nettobeløp i simulering var ${simulertUtbetaling.simulering.nettoBeløp}, bruttobeløp var:${simulertUtbetaling.simulering.bruttoYtelse()}")
            true
        } else false
    }

    override fun gjenopptaUtbetalinger(
        sakId: UUID,
        saksbehandler: NavIdentBruker
    ): Either<KunneIkkeGjenopptaUtbetalinger, Sak> {

        val sak = sakService.hentSak(sakId).getOrElse {
            return KunneIkkeGjenopptaUtbetalinger.FantIkkeSak.left()
        }
        val utbetalingTilSimulering =
            sak.oppdrag.genererUtbetaling(Oppdrag.UtbetalingStrategy.Gjenoppta(saksbehandler), sak.fnr)

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
