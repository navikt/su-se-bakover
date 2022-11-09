package no.nav.su.se.bakover.domain.oppdrag.utbetaling

import no.nav.su.se.bakover.domain.oppdrag.UtbetalingFeilet
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimulerStansFeilet

sealed class UtbetalStansFeil {
    data class KunneIkkeSimulere(val feil: SimulerStansFeilet) : UtbetalStansFeil()
    data class KunneIkkeUtbetale(val feil: UtbetalingFeilet) : UtbetalStansFeil()
}
