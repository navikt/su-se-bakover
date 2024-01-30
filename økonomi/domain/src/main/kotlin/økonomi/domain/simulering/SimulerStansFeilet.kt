package økonomi.domain.simulering

import økonomi.domain.utbetaling.KunneIkkeGenerereUtbetalingsstrategiForStans

sealed interface SimulerStansFeilet {
    data class KunneIkkeSimulere(val feil: SimuleringFeilet) : SimulerStansFeilet
    data class KunneIkkeGenerereUtbetaling(
        val feil: KunneIkkeGenerereUtbetalingsstrategiForStans,
    ) : SimulerStansFeilet
}
