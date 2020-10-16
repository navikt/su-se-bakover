package no.nav.su.se.bakover.domain.oppdrag

import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemmingsnøkkel

sealed class OversendelseTilOppdrag {
    abstract val utbetaling: Utbetaling
    abstract val avstemmingsnøkkel: Avstemmingsnøkkel

    data class TilSimulering(
        override val utbetaling: Utbetaling.UtbetalingForSimulering,
        override val avstemmingsnøkkel: Avstemmingsnøkkel = Avstemmingsnøkkel(),
    ) : OversendelseTilOppdrag()

    data class TilUtbetaling(
        override val utbetaling: Utbetaling.SimulertUtbetaling,
        override val avstemmingsnøkkel: Avstemmingsnøkkel = Avstemmingsnøkkel(),
    ) : OversendelseTilOppdrag()
}
