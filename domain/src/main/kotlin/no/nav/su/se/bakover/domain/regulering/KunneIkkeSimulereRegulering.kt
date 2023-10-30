package no.nav.su.se.bakover.domain.regulering

import no.nav.su.se.bakover.domain.oppdrag.simulering.ForskjellerMellomUtbetalingOgSimulering

sealed interface KunneIkkeSimulereRegulering {
    data object FantIngenBeregning : KunneIkkeSimulereRegulering
    data object SimuleringFeilet : KunneIkkeSimulereRegulering

    data class Forskjeller(val underliggende: ForskjellerMellomUtbetalingOgSimulering) : KunneIkkeSimulereRegulering
}
