package no.nav.su.se.bakover.domain.søknadsbehandling

import no.nav.su.se.bakover.domain.grunnlag.KunneIkkeLageGrunnlagsdata
import no.nav.su.se.bakover.domain.revurdering.OpprettetRevurdering
import no.nav.su.se.bakover.domain.sak.SimulerUtbetalingFeilet
import no.nav.su.se.bakover.domain.vilkår.formue.LeggTilFormuevilkårRequest
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
            val til: KClass<out Søknadsbehandling> = VilkårsvurdertSøknadsbehandling::class,
        ) : KunneIkkeLeggeTilOpplysningsplikt()

        object HeleBehandlingsperiodenErIkkeVurdert : KunneIkkeLeggeTilOpplysningsplikt()
    }

    sealed class KunneIkkeLeggeTilLovligOpphold : KunneIkkeLeggeTilVilkår() {

        sealed class UgyldigTilstand : KunneIkkeLeggeTilLovligOpphold() {
            data class Søknadsbehandling(
                val fra: KClass<out no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling>,
                val til: KClass<out no.nav.su.se.bakover.domain.søknadsbehandling.VilkårsvurdertSøknadsbehandling>,
            ) :
                UgyldigTilstand()

            data class Revurdering(
                val fra: KClass<out no.nav.su.se.bakover.domain.revurdering.Revurdering>,
                val til: KClass<out OpprettetRevurdering>,
            ) :
                UgyldigTilstand()
        }

        object HeleBehandlingsperiodenErIkkeVurdert : KunneIkkeLeggeTilLovligOpphold()
    }

    sealed class KunneIkkeLeggeTilPensjonsVilkår : KunneIkkeLeggeTilVilkår() {
        data class UgyldigTilstand(
            val fra: KClass<out Søknadsbehandling>,
            val til: KClass<out Søknadsbehandling> = VilkårsvurdertSøknadsbehandling::class,
        ) : KunneIkkeLeggeTilPensjonsVilkår()

        object HeleBehandlingsperiodenErIkkeVurdert : KunneIkkeLeggeTilPensjonsVilkår()
        object VilkårKunRelevantForAlder : KunneIkkeLeggeTilPensjonsVilkår()
    }

    sealed class KunneIkkeLeggeTilUførevilkår : KunneIkkeLeggeTilVilkår() {
        data class UgyldigTilstand(
            val fra: KClass<out Søknadsbehandling>,
            val til: KClass<out VilkårsvurdertSøknadsbehandling>,
        ) :
            KunneIkkeLeggeTilUførevilkår()

        object VurderingsperiodeUtenforBehandlingsperiode : KunneIkkeLeggeTilUførevilkår()
    }

    sealed class KunneIkkeLeggeTilInstitusjonsoppholdVilkår : KunneIkkeLeggeTilVilkår() {
        data class UgyldigTilstand(
            val fra: KClass<out Søknadsbehandling>,
            val til: KClass<out VilkårsvurdertSøknadsbehandling> = VilkårsvurdertSøknadsbehandling::class,
        ) : KunneIkkeLeggeTilInstitusjonsoppholdVilkår()

        object BehandlingsperiodeOgVurderingsperiodeMåVæreLik : KunneIkkeLeggeTilInstitusjonsoppholdVilkår()
    }

    sealed class KunneIkkeLeggeTilFormuevilkår : KunneIkkeLeggeTilVilkår() {
        data class UgyldigTilstand(
            val fra: KClass<out Søknadsbehandling>,
            val til: KClass<out Søknadsbehandling>,
        ) : KunneIkkeLeggeTilFormuevilkår()

        data class KunneIkkeMappeTilDomenet(val feil: LeggTilFormuevilkårRequest.KunneIkkeMappeTilDomenet) :
            KunneIkkeLeggeTilFormuevilkår()
    }

    sealed class KunneIkkeLeggeTilFamiliegjenforeningVilkår : KunneIkkeLeggeTilVilkår() {
        data class UgyldigTilstand(
            val fra: KClass<out Søknadsbehandling>,
            val til: KClass<out VilkårsvurdertSøknadsbehandling>,
        ) : KunneIkkeLeggeTilFamiliegjenforeningVilkår()
    }

    sealed class KunneIkkeLeggeTilFlyktningVilkår : KunneIkkeLeggeTilVilkår() {
        data class UgyldigTilstand(
            val fra: KClass<out Søknadsbehandling>,
            val til: KClass<out VilkårsvurdertSøknadsbehandling>,
        ) : KunneIkkeLeggeTilFlyktningVilkår()
    }

    sealed class KunneIkkeLeggeTilFastOppholdINorgeVilkår : KunneIkkeLeggeTilVilkår() {
        data class UgyldigTilstand(
            val fra: KClass<out Søknadsbehandling>,
            val til: KClass<out VilkårsvurdertSøknadsbehandling>,
        ) : KunneIkkeLeggeTilFastOppholdINorgeVilkår()
    }

    sealed class KunneIkkeLeggeTilPersonligOppmøteVilkår : KunneIkkeLeggeTilVilkår() {
        data class UgyldigTilstand(
            val fra: KClass<out Søknadsbehandling>,
            val til: KClass<out VilkårsvurdertSøknadsbehandling>,
        ) : KunneIkkeLeggeTilPersonligOppmøteVilkår()
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
            val til: KClass<out VilkårsvurdertSøknadsbehandling>,
        ) :
            KunneIkkeOppdatereBosituasjon()
    }
}

sealed class KunneIkkeOppdatereStønadsperiode {
    data class UgyldigTilstand(
        val fra: KClass<out Søknadsbehandling>,
        val til: KClass<out VilkårsvurdertSøknadsbehandling> = VilkårsvurdertSøknadsbehandling::class,
    ) : KunneIkkeOppdatereStønadsperiode()

    data class KunneIkkeOppdatereGrunnlagsdata(
        val feil: KunneIkkeLageGrunnlagsdata,
    ) : KunneIkkeOppdatereStønadsperiode()
}

sealed class KunneIkkeBeregne {
    data class UgyldigTilstand(
        val fra: KClass<out Søknadsbehandling>,
        val til: KClass<out BeregnetSøknadsbehandling> = BeregnetSøknadsbehandling::class,
    ) : KunneIkkeBeregne()

    data class UgyldigTilstandForEndringAvFradrag(val feil: KunneIkkeLeggeTilGrunnlag.KunneIkkeLeggeTilFradragsgrunnlag) :
        KunneIkkeBeregne()

    object AvkortingErUfullstendig : KunneIkkeBeregne() {
        override fun toString() = this::class.simpleName!!
    }
}

sealed class KunneIkkeLukkeSøknadsbehandling {
    override fun toString() = this::class.simpleName!!

    object KanIkkeLukkeEnAlleredeLukketSøknadsbehandling : KunneIkkeLukkeSøknadsbehandling()
    object KanIkkeLukkeEnIverksattSøknadsbehandling : KunneIkkeLukkeSøknadsbehandling()
    object KanIkkeLukkeEnSøknadsbehandlingTilAttestering : KunneIkkeLukkeSøknadsbehandling()
}

sealed class KunneIkkeSimulereBehandling {
    data class KunneIkkeSimulere(val feil: SimulerUtbetalingFeilet) : KunneIkkeSimulereBehandling()
    data class UgyldigTilstand(
        val fra: KClass<out Søknadsbehandling>,
        val til: KClass<out SimulertSøknadsbehandling> = SimulertSøknadsbehandling::class,
    ) : KunneIkkeSimulereBehandling()
}
