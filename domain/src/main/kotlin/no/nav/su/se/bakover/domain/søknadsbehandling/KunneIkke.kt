package no.nav.su.se.bakover.domain.søknadsbehandling

import no.nav.su.se.bakover.domain.grunnlag.KunneIkkeLageGrunnlagsdata
import no.nav.su.se.bakover.domain.sak.SimulerUtbetalingFeilet
import kotlin.reflect.KClass

sealed interface ValideringsfeilAttestering {
    data object InneholderUfullstendigBosituasjon : ValideringsfeilAttestering
}

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

    data object AvkortingErUfullstendig : KunneIkkeBeregne
}

sealed interface KunneIkkeLukkeSøknadsbehandling {
    data object KanIkkeLukkeEnAlleredeLukketSøknadsbehandling : KunneIkkeLukkeSøknadsbehandling

    data object KanIkkeLukkeEnIverksattSøknadsbehandling : KunneIkkeLukkeSøknadsbehandling

    data object KanIkkeLukkeEnSøknadsbehandlingTilAttestering : KunneIkkeLukkeSøknadsbehandling
}

sealed interface KunneIkkeSimulereBehandling {
    data class KunneIkkeSimulere(val feil: SimulerUtbetalingFeilet) : KunneIkkeSimulereBehandling
    data class UgyldigTilstand(
        val fra: KClass<out Søknadsbehandling>,
        val til: KClass<out SimulertSøknadsbehandling> = SimulertSøknadsbehandling::class,
    ) : KunneIkkeSimulereBehandling
}

sealed interface KunneIkkeLeggeTilSkattegrunnlag {
    data object KanIkkeLeggeTilSkattForTilstandUtenAtDenHarBlittHentetFør : KunneIkkeLeggeTilSkattegrunnlag

    data object UgyldigTilstand : KunneIkkeLeggeTilSkattegrunnlag
}
