package no.nav.su.se.bakover.service.utbetaling

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.domain.Attestant
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.oppdrag.NyUtbetaling
import no.nav.su.se.bakover.domain.oppdrag.Oppdrag.UtbetalingStrategy.Stans
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemmingsnøkkel
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringClient
import no.nav.su.se.bakover.domain.oppdrag.utbetaling.UtbetalingPublisher
import org.slf4j.LoggerFactory
import java.time.Clock

class StansUtbetalingService(
    private val simuleringClient: SimuleringClient,
    private val clock: Clock = Clock.systemUTC(),
    private val utbetalingPublisher: UtbetalingPublisher,
    private val utbetalingService: UtbetalingService
) {
    object KunneIkkeStanseUtbetalinger

    private val log = LoggerFactory.getLogger(this::class.java)

    fun stansUtbetalinger(
        sak: Sak
    ): Either<KunneIkkeStanseUtbetalinger, Utbetaling> {
        val utbetaling = sak.oppdrag.genererUtbetaling(strategy = Stans(clock), sak.fnr)
        val utbetalingForSimulering = NyUtbetaling(
            oppdrag = sak.oppdrag,
            utbetaling = utbetaling,
            attestant = Attestant("SU"), // Det er ikke nødvendigvis valgt en attestant på dette tidspunktet.
            avstemmingsnøkkel = Avstemmingsnøkkel(Tidspunkt.now(clock))
        )
        val simulertUtbetaling = simuleringClient.simulerUtbetaling(utbetalingForSimulering).fold(
            { return KunneIkkeStanseUtbetalinger.left() },
            { simulering ->
                if (simulering.nettoBeløp != 0 || simulering.bruttoYtelse() != 0) {
                    log.error("Simulering av stansutbetaling der vi sendte inn beløp 0, men nettobeløp i simulering var ${simulering.nettoBeløp}")
                    return KunneIkkeStanseUtbetalinger.left()
                }
                utbetalingService.opprettUtbetaling(sak.oppdrag.id, utbetaling)
                utbetalingService.addSimulering(utbetaling.id, simulering)
            }
        )

        // TODO Her kan vi legge inn transaksjon
        return utbetalingPublisher.publish(
            utbetalingForSimulering.copy(
                utbetaling = simulertUtbetaling
            )
        ).fold(
            {
                log.error("Stansutbetaling feilet ved publisering av utbetaling")
                utbetalingService.addOppdragsmelding(
                    utbetaling.id,
                    it.oppdragsmelding
                )
                KunneIkkeStanseUtbetalinger.left()
            },
            {
                utbetalingService.addOppdragsmelding(utbetaling.id, it).right()
            }
        )
    }
}
