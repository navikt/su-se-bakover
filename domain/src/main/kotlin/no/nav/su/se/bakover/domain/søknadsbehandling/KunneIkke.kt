package no.nav.su.se.bakover.domain.søknadsbehandling

import no.nav.su.se.bakover.domain.grunnlag.KunneIkkeLageGrunnlagsdata
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

sealed interface KunneIkkeOppdatereStønadsperiode {
    data class UgyldigTilstand(
        val fra: KClass<out Søknadsbehandling>,
        val til: KClass<out VilkårsvurdertSøknadsbehandling> = VilkårsvurdertSøknadsbehandling::class,
    ) : KunneIkkeOppdatereStønadsperiode

    data class KunneIkkeOppdatereGrunnlagsdata(
        val feil: KunneIkkeLageGrunnlagsdata,
    ) : KunneIkkeOppdatereStønadsperiode
}

sealed interface KunneIkkeBeregne {

    data class UgyldigTilstandForEndringAvFradrag(val feil: KunneIkkeLeggeTilGrunnlag.KunneIkkeLeggeTilFradragsgrunnlag) :
        KunneIkkeBeregne
}

sealed interface KunneIkkeLukkeSøknadsbehandling {
    data object KanIkkeLukkeEnAlleredeLukketSøknadsbehandling : KunneIkkeLukkeSøknadsbehandling

    data object KanIkkeLukkeEnIverksattSøknadsbehandling : KunneIkkeLukkeSøknadsbehandling

    data object KanIkkeLukkeEnSøknadsbehandlingTilAttestering : KunneIkkeLukkeSøknadsbehandling
}

sealed interface KunneIkkeLeggeTilSkattegrunnlag {
    data object KanIkkeLeggeTilSkattForTilstandUtenAtDenHarBlittHentetFør : KunneIkkeLeggeTilSkattegrunnlag

    data object UgyldigTilstand : KunneIkkeLeggeTilSkattegrunnlag
}
