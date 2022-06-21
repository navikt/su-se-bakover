package no.nav.su.se.bakover.domain.søknadsbehandling

import no.nav.su.se.bakover.domain.grunnlag.KunneIkkeLageGrunnlagsdata
import kotlin.reflect.KClass

sealed class KunneIkkeLeggeTilVilkår {
    sealed class KunneIkkeLeggeTilUtenlandsopphold : KunneIkkeLeggeTilVilkår() {
        data class IkkeLovÅLeggeTilUtenlandsoppholdIDenneStatusen(
            val fra: KClass<out Søknadsbehandling>,
            val til: KClass<out Søknadsbehandling>,
        ) : KunneIkkeLeggeTilUtenlandsopphold()

        object VurderingsperiodeUtenforBehandlingsperiode : KunneIkkeLeggeTilUtenlandsopphold()
        object MåInneholdeKunEnVurderingsperiode : KunneIkkeLeggeTilUtenlandsopphold()
        object AlleVurderingsperioderMåHaSammeResultat : KunneIkkeLeggeTilUtenlandsopphold()
        object MåVurdereHelePerioden : KunneIkkeLeggeTilUtenlandsopphold()
    }

    sealed class KunneIkkeLeggeTilOpplysningsplikt : KunneIkkeLeggeTilVilkår() {
        data class UgyldigTilstand(
            val fra: KClass<out Søknadsbehandling>,
            val til: KClass<out Søknadsbehandling> = Søknadsbehandling.Vilkårsvurdert::class,
        ) : KunneIkkeLeggeTilOpplysningsplikt()

        object HeleBehandlingsperiodenErIkkeVurdert : KunneIkkeLeggeTilOpplysningsplikt()
    }

    sealed class KunneIkkeLeggeTilPensjonsVilkår : KunneIkkeLeggeTilVilkår() {
        data class UgyldigTilstand(
            val fra: KClass<out Søknadsbehandling>,
            val til: KClass<out Søknadsbehandling> = Søknadsbehandling.Vilkårsvurdert::class,
        ) : KunneIkkeLeggeTilPensjonsVilkår()

        object HeleBehandlingsperiodenErIkkeVurdert : KunneIkkeLeggeTilPensjonsVilkår()
        object VilkårKunRelevantForAlder : KunneIkkeLeggeTilPensjonsVilkår()
    }

    sealed class KunneIkkeLeggeTilUførevilkår : KunneIkkeLeggeTilVilkår() {
        data class UgyldigTilstand(
            val fra: KClass<out Søknadsbehandling>,
            val til: KClass<out Søknadsbehandling.Vilkårsvurdert>,
        ) :
            KunneIkkeLeggeTilUførevilkår()

        object VurderingsperiodeUtenforBehandlingsperiode : KunneIkkeLeggeTilUførevilkår()
    }

    sealed class KunneIkkeLeggeTilFormuevilkår : KunneIkkeLeggeTilVilkår() {
        data class UgyldigTilstand(
            val fra: KClass<out Søknadsbehandling>,
            val til: KClass<out Søknadsbehandling>,
        ) : KunneIkkeLeggeTilFormuevilkår()
    }
}

sealed class KunneIkkeLeggeTilGrunnlag {
    sealed class KunneIkkeLeggeTilFradragsgrunnlag : KunneIkkeLeggeTilGrunnlag() {
        data class IkkeLovÅLeggeTilFradragIDenneStatusen(
            val status: KClass<out Søknadsbehandling>,
        ) : KunneIkkeLeggeTilFradragsgrunnlag()

        object GrunnlagetMåVæreInnenforBehandlingsperioden : KunneIkkeLeggeTilFradragsgrunnlag()
        data class KunneIkkeEndreFradragsgrunnlag(val feil: KunneIkkeLageGrunnlagsdata) :
            KunneIkkeLeggeTilFradragsgrunnlag()
    }

    sealed class KunneIkkeOppdatereBosituasjon : KunneIkkeLeggeTilGrunnlag() {
        data class UgyldigTilstand(
            val fra: KClass<out Søknadsbehandling>,
            val til: KClass<out Søknadsbehandling.Vilkårsvurdert>,
        ) :
            KunneIkkeOppdatereBosituasjon()
    }
}
