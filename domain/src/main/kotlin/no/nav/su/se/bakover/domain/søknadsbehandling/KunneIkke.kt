package no.nav.su.se.bakover.domain.søknadsbehandling

import no.nav.su.se.bakover.domain.grunnlag.KunneIkkeLageGrunnlagsdata
import no.nav.su.se.bakover.domain.revurdering.OpprettetRevurdering
import no.nav.su.se.bakover.domain.sak.SimulerUtbetalingFeilet
import no.nav.su.se.bakover.domain.vilkår.formue.LeggTilFormuevilkårRequest
import kotlin.reflect.KClass

sealed interface ValideringsfeilAttestering {
    object InneholderUfullstendigBosituasjon : ValideringsfeilAttestering
}

sealed interface KunneIkkeLeggeTilVilkår {
    sealed interface KunneIkkeLeggeTilUtenlandsopphold : KunneIkkeLeggeTilVilkår {
        data class IkkeLovÅLeggeTilUtenlandsoppholdIDenneStatusen(
            val fra: KClass<out Søknadsbehandling>,
            val til: KClass<out Søknadsbehandling>,
        ) : KunneIkkeLeggeTilUtenlandsopphold

        object VurderingsperiodeUtenforBehandlingsperiode : KunneIkkeLeggeTilUtenlandsopphold
        object MåInneholdeKunEnVurderingsperiode : KunneIkkeLeggeTilUtenlandsopphold
        object AlleVurderingsperioderMåHaSammeResultat : KunneIkkeLeggeTilUtenlandsopphold
        object MåVurdereHelePerioden : KunneIkkeLeggeTilUtenlandsopphold
    }

    sealed interface KunneIkkeLeggeTilOpplysningsplikt : KunneIkkeLeggeTilVilkår {
        data class UgyldigTilstand(
            val fra: KClass<out Søknadsbehandling>,
            val til: KClass<out Søknadsbehandling> = VilkårsvurdertSøknadsbehandling::class,
        ) : KunneIkkeLeggeTilOpplysningsplikt

        object HeleBehandlingsperiodenErIkkeVurdert : KunneIkkeLeggeTilOpplysningsplikt
    }

    sealed interface KunneIkkeLeggeTilLovligOpphold : KunneIkkeLeggeTilVilkår {

        sealed interface UgyldigTilstand : KunneIkkeLeggeTilLovligOpphold {
            data class Søknadsbehandling(
                val fra: KClass<out no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling>,
                val til: KClass<out VilkårsvurdertSøknadsbehandling>,
            ) : UgyldigTilstand

            data class Revurdering(
                val fra: KClass<out no.nav.su.se.bakover.domain.revurdering.Revurdering>,
                val til: KClass<out OpprettetRevurdering>,
            ) : UgyldigTilstand
        }

        object HeleBehandlingsperiodenErIkkeVurdert : KunneIkkeLeggeTilLovligOpphold
    }

    sealed interface KunneIkkeLeggeTilPensjonsVilkår : KunneIkkeLeggeTilVilkår {
        data class UgyldigTilstand(
            val fra: KClass<out Søknadsbehandling>,
            val til: KClass<out Søknadsbehandling> = VilkårsvurdertSøknadsbehandling::class,
        ) : KunneIkkeLeggeTilPensjonsVilkår

        object HeleBehandlingsperiodenErIkkeVurdert : KunneIkkeLeggeTilPensjonsVilkår
        object VilkårKunRelevantForAlder : KunneIkkeLeggeTilPensjonsVilkår
    }

    sealed interface KunneIkkeLeggeTilUførevilkår : KunneIkkeLeggeTilVilkår {
        data class UgyldigTilstand(
            val fra: KClass<out Søknadsbehandling>,
            val til: KClass<out VilkårsvurdertSøknadsbehandling>,
        ) :
            KunneIkkeLeggeTilUførevilkår

        object VurderingsperiodeUtenforBehandlingsperiode : KunneIkkeLeggeTilUførevilkår
    }

    sealed interface KunneIkkeLeggeTilInstitusjonsoppholdVilkår : KunneIkkeLeggeTilVilkår {
        data class UgyldigTilstand(
            val fra: KClass<out Søknadsbehandling>,
            val til: KClass<out VilkårsvurdertSøknadsbehandling> = VilkårsvurdertSøknadsbehandling::class,
        ) : KunneIkkeLeggeTilInstitusjonsoppholdVilkår

        object BehandlingsperiodeOgVurderingsperiodeMåVæreLik : KunneIkkeLeggeTilInstitusjonsoppholdVilkår
    }

    sealed interface KunneIkkeLeggeTilFormuevilkår : KunneIkkeLeggeTilVilkår {
        data class UgyldigTilstand(
            val fra: KClass<out Søknadsbehandling>,
            val til: KClass<out Søknadsbehandling>,
        ) : KunneIkkeLeggeTilFormuevilkår

        data class KunneIkkeMappeTilDomenet(val feil: LeggTilFormuevilkårRequest.KunneIkkeMappeTilDomenet) :
            KunneIkkeLeggeTilFormuevilkår
    }

    sealed interface KunneIkkeLeggeTilFamiliegjenforeningVilkår : KunneIkkeLeggeTilVilkår {
        data class UgyldigTilstand(
            val fra: KClass<out Søknadsbehandling>,
            val til: KClass<out VilkårsvurdertSøknadsbehandling>,
        ) : KunneIkkeLeggeTilFamiliegjenforeningVilkår
    }

    sealed interface KunneIkkeLeggeTilFlyktningVilkår : KunneIkkeLeggeTilVilkår {
        data class UgyldigTilstand(
            val fra: KClass<out Søknadsbehandling>,
            val til: KClass<out VilkårsvurdertSøknadsbehandling>,
        ) : KunneIkkeLeggeTilFlyktningVilkår
    }

    sealed interface KunneIkkeLeggeTilFastOppholdINorgeVilkår : KunneIkkeLeggeTilVilkår {
        data class UgyldigTilstand(
            val fra: KClass<out Søknadsbehandling>,
            val til: KClass<out VilkårsvurdertSøknadsbehandling>,
        ) : KunneIkkeLeggeTilFastOppholdINorgeVilkår
    }

    sealed interface KunneIkkeLeggeTilPersonligOppmøteVilkår : KunneIkkeLeggeTilVilkår {
        data class UgyldigTilstand(
            val fra: KClass<out Søknadsbehandling>,
            val til: KClass<out VilkårsvurdertSøknadsbehandling>,
        ) : KunneIkkeLeggeTilPersonligOppmøteVilkår
    }
}

sealed interface KunneIkkeLeggeTilGrunnlag {
    sealed interface KunneIkkeLeggeTilFradragsgrunnlag : KunneIkkeLeggeTilGrunnlag {
        data class IkkeLovÅLeggeTilFradragIDenneStatusen(
            val status: KClass<out Søknadsbehandling>,
        ) : KunneIkkeLeggeTilFradragsgrunnlag

        object GrunnlagetMåVæreInnenforBehandlingsperioden : KunneIkkeLeggeTilFradragsgrunnlag
        data class KunneIkkeEndreFradragsgrunnlag(val feil: KunneIkkeLageGrunnlagsdata) :
            KunneIkkeLeggeTilFradragsgrunnlag
    }

    sealed interface KunneIkkeOppdatereBosituasjon : KunneIkkeLeggeTilGrunnlag {
        data class UgyldigTilstand(
            val fra: KClass<out Søknadsbehandling>,
            val til: KClass<out VilkårsvurdertSøknadsbehandling>,
        ) : KunneIkkeOppdatereBosituasjon

        object GrunnlagetMåVæreInnenforBehandlingsperioden : KunneIkkeOppdatereBosituasjon
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

    object AvkortingErUfullstendig : KunneIkkeBeregne {
        override fun toString() = this::class.simpleName!!
    }
}

sealed interface KunneIkkeLukkeSøknadsbehandling {
    object KanIkkeLukkeEnAlleredeLukketSøknadsbehandling : KunneIkkeLukkeSøknadsbehandling {
        override fun toString() = this::class.simpleName!!
    }

    object KanIkkeLukkeEnIverksattSøknadsbehandling : KunneIkkeLukkeSøknadsbehandling {
        override fun toString() = this::class.simpleName!!
    }

    object KanIkkeLukkeEnSøknadsbehandlingTilAttestering : KunneIkkeLukkeSøknadsbehandling {
        override fun toString() = this::class.simpleName!!
    }
}

sealed interface KunneIkkeSimulereBehandling {
    data class KunneIkkeSimulere(val feil: SimulerUtbetalingFeilet) : KunneIkkeSimulereBehandling
    data class UgyldigTilstand(
        val fra: KClass<out Søknadsbehandling>,
        val til: KClass<out SimulertSøknadsbehandling> = SimulertSøknadsbehandling::class,
    ) : KunneIkkeSimulereBehandling
}

sealed interface KunneIkkeLeggeTilSkattegrunnlag {
    object KanIkkeLeggeTilSkattForTilstandUtenAtDenHarBlittHentetFør : KunneIkkeLeggeTilSkattegrunnlag
    object UgyldigTilstand : KunneIkkeLeggeTilSkattegrunnlag
}
