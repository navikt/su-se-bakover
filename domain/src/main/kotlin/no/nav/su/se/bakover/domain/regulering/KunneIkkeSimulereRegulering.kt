package no.nav.su.se.bakover.domain.regulering

sealed interface KunneIkkeSimulereRegulering {
    object FantIngenBeregning : KunneIkkeSimulereRegulering
    object SimuleringFeilet : KunneIkkeSimulereRegulering
}
