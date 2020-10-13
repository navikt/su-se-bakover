package no.nav.su.se.bakover.service.utbetaling

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.domain.Attestant
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.oppdrag.NyUtbetaling
import no.nav.su.se.bakover.domain.oppdrag.Oppdrag.UtbetalingStrategy.Gjenoppta
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemmingsnøkkel
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringClient
import no.nav.su.se.bakover.domain.oppdrag.utbetaling.UtbetalingPublisher
import no.nav.su.se.bakover.service.sak.SakService
import org.slf4j.LoggerFactory
import java.time.Clock
import java.util.UUID

class StartUtbetalingerService(
    private val simuleringClient: SimuleringClient,
    private val utbetalingPublisher: UtbetalingPublisher,
    private val utbetalingService: UtbetalingService,
    private val sakService: SakService,
    private val clock: Clock = Clock.systemUTC()
) {
    private val log = LoggerFactory.getLogger(this::class.java)

    fun startUtbetalinger(sakId: UUID): Either<StartUtbetalingFeilet, Sak> {
        val sak = sakService.hentSak(sakId).fold(
            { return StartUtbetalingFeilet.FantIkkeSak.left() },
            { it }
        )
        val sisteOversendteUtbetaling = sak.oppdrag.sisteOversendteUtbetaling()
            ?: return StartUtbetalingFeilet.HarIngenOversendteUtbetalinger.left()

        if (sisteOversendteUtbetaling !is Utbetaling.Stans) return StartUtbetalingFeilet.SisteUtbetalingErIkkeEnStansutbetaling.left()

        val utbetaling = sak.oppdrag.genererUtbetaling(Gjenoppta, sak.fnr)

        val nyUtbetaling = NyUtbetaling(
            oppdrag = sak.oppdrag,
            utbetaling = utbetaling,
            attestant = Attestant("SU"), // Det er ikke nødvendigvis valgt en attestant på dette tidspunktet.
            avstemmingsnøkkel = Avstemmingsnøkkel(Tidspunkt.now(clock))
        )

        val simulertUtbetaling = simuleringClient.simulerUtbetaling(nyUtbetaling).fold(
            { return StartUtbetalingFeilet.SimuleringAvStartutbetalingFeilet.left() },
            { simulering ->
                utbetalingService.opprettUtbetaling(sak.oppdrag.id, utbetaling)
                utbetalingService.addSimulering(utbetaling.id, simulering)
            }
        )

        return utbetalingPublisher.publish(
            nyUtbetaling.copy(
                utbetaling = simulertUtbetaling
            )
        ).fold(
            {
                log.error("Startutbetaling feilet ved publisering av utbetaling")
                utbetalingService.addOppdragsmelding(utbetaling.id, it.oppdragsmelding)
                StartUtbetalingFeilet.SendingAvUtebetalingTilOppdragFeilet.left()
            },
            {
                val utbetalingMedOppdragsMelding = utbetalingService.addOppdragsmelding(utbetaling.id, it)
                // TODO jah: Burde kunne slippe
                sak.copy(
                    oppdrag = sak.oppdrag.copy(
                        utbetalinger = sak.oppdrag.hentUtbetalinger() + utbetalingMedOppdragsMelding,
                    ),
                ).right()
            }
        )
    }
}

sealed class StartUtbetalingFeilet {
    object FantIkkeSak : StartUtbetalingFeilet()
    object HarIngenOversendteUtbetalinger : StartUtbetalingFeilet()
    object SisteUtbetalingErIkkeEnStansutbetaling : StartUtbetalingFeilet()
    object SimuleringAvStartutbetalingFeilet : StartUtbetalingFeilet()
    object SendingAvUtebetalingTilOppdragFeilet : StartUtbetalingFeilet()
}
