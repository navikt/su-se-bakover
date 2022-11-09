package no.nav.su.se.bakover.domain.oppdrag.utbetaling

import no.nav.su.se.bakover.domain.oppdrag.UtbetalingFeilet
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimulerGjenopptakFeil

sealed class UtbetalGjenopptakFeil {
    data class KunneIkkeSimulere(val feil: SimulerGjenopptakFeil) : UtbetalGjenopptakFeil()
    data class KunneIkkeUtbetale(val feil: UtbetalingFeilet) : UtbetalGjenopptakFeil()
}
