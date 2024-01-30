package økonomi.domain.utbetaling

import økonomi.domain.simulering.SimulerStansFeilet

sealed interface UtbetalStansFeil {
    data class KunneIkkeSimulere(val feil: SimulerStansFeilet) : UtbetalStansFeil
    data class KunneIkkeUtbetale(val feil: UtbetalingFeilet) : UtbetalStansFeil
}
