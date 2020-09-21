package no.nav.su.se.bakover.domain.oppdrag

import no.nav.su.se.bakover.domain.Attestant

data class NyUtbetaling(
    val oppdrag: Oppdrag,
    val utbetaling: Utbetaling,
    val attestant: Attestant
)
