package no.nav.su.se.bakover.domain.regulering

sealed interface KunneIkkeBeregneRegulering {
    data class BeregningFeilet(val feil: Throwable) : KunneIkkeBeregneRegulering
}
