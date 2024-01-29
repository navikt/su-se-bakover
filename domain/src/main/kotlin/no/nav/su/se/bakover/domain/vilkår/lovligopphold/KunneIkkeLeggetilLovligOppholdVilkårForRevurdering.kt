package no.nav.su.se.bakover.domain.vilkår.lovligopphold

import no.nav.su.se.bakover.domain.revurdering.vilkår.opphold.KunneIkkeOppdatereLovligOppholdOgMarkereSomVurdert
import vilkår.lovligopphold.domain.KunneIkkeLageLovligOppholdVilkår

/**
 * TODO jah: Refaktorer i forbindelse med [KunneIkkeLeggetilLovligOppholdVilkårForSøknadsbehandling]. Finn de naturlige grensene mellom søknadsbehandling og revurdering.
 */
sealed interface KunneIkkeLeggetilLovligOppholdVilkårForRevurdering {
    data object FantIkkeBehandling : KunneIkkeLeggetilLovligOppholdVilkårForRevurdering

    data class UgyldigLovligOppholdVilkår(
        val underliggende: KunneIkkeLageLovligOppholdVilkår,
    ) : KunneIkkeLeggetilLovligOppholdVilkårForRevurdering

    data class Domenefeil(
        val underliggende: KunneIkkeOppdatereLovligOppholdOgMarkereSomVurdert,
    ) : KunneIkkeLeggetilLovligOppholdVilkårForRevurdering
}
