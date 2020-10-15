package no.nav.su.se.bakover.domain.oppdrag

import no.nav.su.se.bakover.domain.Attestant
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemmingsnøkkel
// TODO separate types for simulering and utbetaling?
data class NyUtbetaling(
    val oppdrag: Oppdrag,
    val utbetaling: Utbetaling,
    val attestant: Attestant,
    val avstemmingsnøkkel: Avstemmingsnøkkel = Avstemmingsnøkkel()
)
