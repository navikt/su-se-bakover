package no.nav.su.se.bakover.domain.oppdrag

import no.nav.su.se.bakover.domain.Attestant
import no.nav.su.se.bakover.domain.Fnr

data class NyUtbetaling(
    val oppdrag: Oppdrag,
    val utbetaling: Utbetaling,
    val oppdragGjelder: Fnr,
    val attestant: Attestant
)
