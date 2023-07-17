package no.nav.su.se.bakover.domain.søknadsbehandling.vilkår

import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.domain.vilkår.formue.LeggTilFormuevilkårRequest
import kotlin.reflect.KClass

sealed interface KunneIkkeLeggeTilVilkår {
    sealed interface KunneIkkeLeggeTilUtenlandsopphold : KunneIkkeLeggeTilVilkår {

        data object MåInneholdeKunEnVurderingsperiode : KunneIkkeLeggeTilUtenlandsopphold

        data class Vilkårsfeil(val underliggende: VilkårsfeilVedSøknadsbehandling) : KunneIkkeLeggeTilUtenlandsopphold
    }

    sealed interface KunneIkkeLeggeTilOpplysningsplikt : KunneIkkeLeggeTilVilkår {
        data class Vilkårsfeil(val underliggende: VilkårsfeilVedSøknadsbehandling) : KunneIkkeLeggeTilOpplysningsplikt
    }

    sealed interface KunneIkkeLeggeTilLovligOpphold : KunneIkkeLeggeTilVilkår {
        data class Vilkårsfeil(val underliggende: VilkårsfeilVedSøknadsbehandling) : KunneIkkeLeggeTilLovligOpphold
    }

    sealed interface KunneIkkeLeggeTilPensjonsVilkår : KunneIkkeLeggeTilVilkår {
        data object VilkårKunRelevantForAlder : KunneIkkeLeggeTilPensjonsVilkår

        data class Vilkårsfeil(val underliggende: VilkårsfeilVedSøknadsbehandling) : KunneIkkeLeggeTilPensjonsVilkår
    }

    sealed interface KunneIkkeLeggeTilUførevilkår : KunneIkkeLeggeTilVilkår {
        data class Vilkårsfeil(val underliggende: VilkårsfeilVedSøknadsbehandling) : KunneIkkeLeggeTilUførevilkår
    }

    sealed interface KunneIkkeLeggeTilInstitusjonsoppholdVilkår : KunneIkkeLeggeTilVilkår {
        data class Vilkårsfeil(
            val underliggende: VilkårsfeilVedSøknadsbehandling,
        ) : KunneIkkeLeggeTilInstitusjonsoppholdVilkår
    }

    sealed interface KunneIkkeLeggeTilFormuevilkår : KunneIkkeLeggeTilVilkår {
        data class UgyldigTilstand(
            val fra: KClass<out Søknadsbehandling>,
            val til: KClass<out Søknadsbehandling>,
        ) : KunneIkkeLeggeTilFormuevilkår

        data class KunneIkkeMappeTilDomenet(
            val feil: LeggTilFormuevilkårRequest.KunneIkkeMappeTilDomenet,
        ) : KunneIkkeLeggeTilFormuevilkår
    }

    sealed interface KunneIkkeLeggeTilFamiliegjenforeningVilkår : KunneIkkeLeggeTilVilkår {
        data class Vilkårsfeil(
            val underliggende: VilkårsfeilVedSøknadsbehandling,
        ) : KunneIkkeLeggeTilFamiliegjenforeningVilkår
    }

    sealed interface KunneIkkeLeggeTilFlyktningVilkår : KunneIkkeLeggeTilVilkår {
        data class Vilkårsfeil(val underliggende: VilkårsfeilVedSøknadsbehandling) : KunneIkkeLeggeTilFlyktningVilkår
    }

    sealed interface KunneIkkeLeggeTilFastOppholdINorgeVilkår : KunneIkkeLeggeTilVilkår {
        data class Vilkårsfeil(
            val underliggende: VilkårsfeilVedSøknadsbehandling,
        ) : KunneIkkeLeggeTilFastOppholdINorgeVilkår
    }

    sealed interface KunneIkkeLeggeTilPersonligOppmøteVilkår : KunneIkkeLeggeTilVilkår {
        data class Vilkårsfeil(
            val underliggende: VilkårsfeilVedSøknadsbehandling,
        ) : KunneIkkeLeggeTilPersonligOppmøteVilkår
    }
}
