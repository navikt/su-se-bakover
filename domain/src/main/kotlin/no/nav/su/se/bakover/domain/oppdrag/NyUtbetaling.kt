package no.nav.su.se.bakover.domain.oppdrag

import no.nav.su.se.bakover.domain.Attestant
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemmingsnøkkel

data class NyUtbetaling(
    val oppdrag: Oppdrag,
    val utbetaling: Utbetaling,
    val attestant: Attestant,
    val avstemmingsnøkkel: Avstemmingsnøkkel = Avstemmingsnøkkel()
)
