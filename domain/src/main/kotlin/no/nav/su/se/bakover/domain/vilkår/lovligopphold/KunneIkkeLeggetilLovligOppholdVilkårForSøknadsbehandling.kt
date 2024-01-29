package no.nav.su.se.bakover.domain.vilkår.lovligopphold

import no.nav.su.se.bakover.domain.søknadsbehandling.vilkår.KunneIkkeLeggeTilVilkår
import vilkår.lovligopphold.domain.KunneIkkeLageLovligOppholdVilkår

/**
 * TODO jah: Refaktorer i forbindelse med [KunneIkkeLeggetilLovligOppholdVilkårForRevurdering]. Finn de naturlige grensene mellom søknadsbehandling og revurdering.
 */
sealed interface KunneIkkeLeggetilLovligOppholdVilkårForSøknadsbehandling {
    data object FantIkkeBehandling : KunneIkkeLeggetilLovligOppholdVilkårForSøknadsbehandling

    data class UgyldigLovligOppholdVilkår(
        val feil: KunneIkkeLageLovligOppholdVilkår,
    ) : KunneIkkeLeggetilLovligOppholdVilkårForSøknadsbehandling

    data class FeilVedSøknadsbehandling(
        val feil: KunneIkkeLeggeTilVilkår.KunneIkkeLeggeTilLovligOpphold,
    ) : KunneIkkeLeggetilLovligOppholdVilkårForSøknadsbehandling
}
