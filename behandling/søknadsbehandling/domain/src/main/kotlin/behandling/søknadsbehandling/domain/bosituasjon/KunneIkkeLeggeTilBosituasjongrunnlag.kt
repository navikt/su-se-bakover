package behandling.søknadsbehandling.domain.bosituasjon

import no.nav.su.se.bakover.behandling.Stønadsbehandling

sealed interface KunneIkkeLeggeTilBosituasjongrunnlag {
    data class UgyldigTilstand(
        val fra: kotlin.reflect.KClass<out Stønadsbehandling>,
        val til: kotlin.reflect.KClass<out Stønadsbehandling>,
    ) : KunneIkkeLeggeTilBosituasjongrunnlag

    data object FantIkkeBehandling : KunneIkkeLeggeTilBosituasjongrunnlag
    data object UgyldigData : KunneIkkeLeggeTilBosituasjongrunnlag
    data object KunneIkkeSlåOppEPS : KunneIkkeLeggeTilBosituasjongrunnlag
    data object EpsAlderErNull : KunneIkkeLeggeTilBosituasjongrunnlag
    data object GrunnlagetMåVæreInnenforBehandlingsperioden : KunneIkkeLeggeTilBosituasjongrunnlag
}
