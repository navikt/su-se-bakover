package behandling.regulering.domain.simulering

import økonomi.domain.simulering.ForskjellerMellomUtbetalingOgSimulering

sealed interface KunneIkkeSimulereRegulering {
    data object FantIngenBeregning : KunneIkkeSimulereRegulering
    data object ManglerUføreGrunnlag : KunneIkkeSimulereRegulering
    data object SimuleringFeilet : KunneIkkeSimulereRegulering

    data class Forskjeller(val underliggende: ForskjellerMellomUtbetalingOgSimulering) : KunneIkkeSimulereRegulering
}
