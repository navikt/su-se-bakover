package behandling.regulering.domain.beregning

sealed interface KunneIkkeBeregneRegulering {
    data class BeregningFeilet(val feil: Throwable) : KunneIkkeBeregneRegulering
}
