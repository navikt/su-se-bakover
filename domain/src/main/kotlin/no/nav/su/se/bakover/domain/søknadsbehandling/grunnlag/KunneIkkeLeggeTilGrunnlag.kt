package no.nav.su.se.bakover.domain.søknadsbehandling.grunnlag

import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.VilkårsvurdertSøknadsbehandling
import vilkår.vurderinger.domain.KunneIkkeLageGrunnlagsdata
import kotlin.reflect.KClass

sealed interface KunneIkkeLeggeTilGrunnlag {
    sealed interface KunneIkkeLeggeTilFradragsgrunnlag : KunneIkkeLeggeTilGrunnlag {
        data class IkkeLovÅLeggeTilFradragIDenneStatusen(
            val status: KClass<out Søknadsbehandling>,
        ) : KunneIkkeLeggeTilFradragsgrunnlag

        data object GrunnlagetMåVæreInnenforBehandlingsperioden : KunneIkkeLeggeTilFradragsgrunnlag

        data class KunneIkkeEndreFradragsgrunnlag(val feil: KunneIkkeLageGrunnlagsdata) :
            KunneIkkeLeggeTilFradragsgrunnlag
    }

    sealed interface KunneIkkeOppdatereBosituasjon : KunneIkkeLeggeTilGrunnlag {
        data class UgyldigTilstand(
            val fra: KClass<out Søknadsbehandling>,
            val til: KClass<out VilkårsvurdertSøknadsbehandling>,
        ) : KunneIkkeOppdatereBosituasjon

        data object GrunnlagetMåVæreInnenforBehandlingsperioden : KunneIkkeOppdatereBosituasjon
    }
}
