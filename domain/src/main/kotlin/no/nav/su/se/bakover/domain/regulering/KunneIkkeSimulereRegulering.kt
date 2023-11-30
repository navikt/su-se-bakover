package no.nav.su.se.bakover.domain.regulering

import økonomi.domain.simulering.ForskjellerMellomUtbetalingOgSimulering

sealed interface KunneIkkeSimulereRegulering {
    data object FantIngenBeregning : KunneIkkeSimulereRegulering
    data object SimuleringFeilet : KunneIkkeSimulereRegulering

    data class Forskjeller(val underliggende: ForskjellerMellomUtbetalingOgSimulering) : KunneIkkeSimulereRegulering
}
