package behandling.revurdering.domain.bosituasjon

sealed interface KunneIkkeLeggeTilBosituasjongrunnlagForRevurdering {
    data object FantIkkeBehandling : KunneIkkeLeggeTilBosituasjongrunnlagForRevurdering
    data object UgyldigData : KunneIkkeLeggeTilBosituasjongrunnlagForRevurdering
    data object KunneIkkeSlåOppEPS : KunneIkkeLeggeTilBosituasjongrunnlagForRevurdering
    data object EpsAlderErNull : KunneIkkeLeggeTilBosituasjongrunnlagForRevurdering

    data class KunneIkkeLeggeTilBosituasjon(
        val feil: KunneIkkeLeggeTilBosituasjonForRevurdering,
    ) : KunneIkkeLeggeTilBosituasjongrunnlagForRevurdering
}
