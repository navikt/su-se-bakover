package no.nav.su.se.bakover.domain.regulering

sealed interface KunneIkkeSimulereRegulering {
    data object FantIngenBeregning : KunneIkkeSimulereRegulering
    data object SimuleringFeilet : KunneIkkeSimulereRegulering
}
