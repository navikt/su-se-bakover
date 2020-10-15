package no.nav.su.se.bakover.domain.oppdrag

import no.nav.su.se.bakover.domain.Attestant
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemmingsnøkkel

sealed class OversendelseTilOppdrag {
    abstract val oppdrag: Oppdrag
    abstract val utbetaling: Utbetaling
    abstract val attestant: Attestant
    abstract val avstemmingsnøkkel: Avstemmingsnøkkel

    data class NyUtbetaling(
        override val oppdrag: Oppdrag,
        override val utbetaling: Utbetaling,
        override val attestant: Attestant,
        override val avstemmingsnøkkel: Avstemmingsnøkkel = Avstemmingsnøkkel()
    ) : OversendelseTilOppdrag()

    data class TilUtbetaling(
        override val oppdrag: Oppdrag,
        override val utbetaling: Utbetaling.SimulertUtbetaling,
        override val attestant: Attestant,
        override val avstemmingsnøkkel: Avstemmingsnøkkel = Avstemmingsnøkkel()
    ) : OversendelseTilOppdrag()
}
