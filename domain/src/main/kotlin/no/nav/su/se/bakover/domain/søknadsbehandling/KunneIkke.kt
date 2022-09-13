package no.nav.su.se.bakover.domain.søknadsbehandling

import no.nav.su.se.bakover.domain.grunnlag.KunneIkkeLageGrunnlagsdata
import no.nav.su.se.bakover.domain.oppdrag.UtbetalingFeilet
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringFeilet
import no.nav.su.se.bakover.domain.revurdering.OpprettetRevurdering
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

    sealed class KunneIkkeLeggeTilLovligOpphold : KunneIkkeLeggeTilVilkår() {

        sealed class UgyldigTilstand : KunneIkkeLeggeTilLovligOpphold() {
            data class Søknadsbehandling(
                val fra: KClass<out no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling>,
                val til: KClass<out no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling.Vilkårsvurdert>,
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

    sealed class KunneIkkeLeggeTilInstitusjonsoppholdVilkår : KunneIkkeLeggeTilVilkår() {
        data class UgyldigTilstand(
            val fra: KClass<out Søknadsbehandling>,
            val til: KClass<out Søknadsbehandling.Vilkårsvurdert> = Søknadsbehandling.Vilkårsvurdert::class,
        ) : KunneIkkeLeggeTilInstitusjonsoppholdVilkår()

        object BehandlingsperiodeOgVurderingsperiodeMåVæreLik : KunneIkkeLeggeTilInstitusjonsoppholdVilkår()
    }

    sealed class KunneIkkeLeggeTilFormuevilkår : KunneIkkeLeggeTilVilkår() {
        data class UgyldigTilstand(
            val fra: KClass<out Søknadsbehandling>,
            val til: KClass<out Søknadsbehandling>,
        ) : KunneIkkeLeggeTilFormuevilkår()
    }

    sealed class KunneIkkeLeggeTilFamiliegjenforeningVilkår : KunneIkkeLeggeTilVilkår() {
        data class UgyldigTilstand(
            val fra: KClass<out Søknadsbehandling>,
            val til: KClass<out Søknadsbehandling.Vilkårsvurdert>,
        ) : KunneIkkeLeggeTilFamiliegjenforeningVilkår()
    }

    sealed class KunneIkkeLeggeTilFlyktningVilkår : KunneIkkeLeggeTilVilkår() {
        data class UgyldigTilstand(
            val fra: KClass<out Søknadsbehandling>,
            val til: KClass<out Søknadsbehandling.Vilkårsvurdert>,
        ) : KunneIkkeLeggeTilFlyktningVilkår()
    }

    sealed class KunneIkkeLeggeTilFastOppholdINorgeVilkår : KunneIkkeLeggeTilVilkår() {
        data class UgyldigTilstand(
            val fra: KClass<out Søknadsbehandling>,
            val til: KClass<out Søknadsbehandling.Vilkårsvurdert>,
        ) : KunneIkkeLeggeTilFastOppholdINorgeVilkår()
    }

    sealed class KunneIkkeLeggeTilPersonligOppmøteVilkår : KunneIkkeLeggeTilVilkår() {
        data class UgyldigTilstand(
            val fra: KClass<out Søknadsbehandling>,
            val til: KClass<out Søknadsbehandling.Vilkårsvurdert>,
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
            val til: KClass<out Søknadsbehandling.Vilkårsvurdert>,
        ) :
            KunneIkkeOppdatereBosituasjon()
    }
}

sealed class KunneIkkeOppdatereStønadsperiode {
    data class UgyldigTilstand(
        val fra: KClass<out Søknadsbehandling>,
        val til: KClass<out Søknadsbehandling.Vilkårsvurdert> = Søknadsbehandling.Vilkårsvurdert::class,
    ) : KunneIkkeOppdatereStønadsperiode()

    data class KunneIkkeOppdatereGrunnlagsdata(
        val feil: KunneIkkeLageGrunnlagsdata,
    ) : KunneIkkeOppdatereStønadsperiode()
}

sealed interface KunneIkkeIverksette {
    object AttestantOgSaksbehandlerKanIkkeVæreSammePerson : KunneIkkeIverksette
    data class KunneIkkeUtbetale(val utbetalingFeilet: UtbetalingFeilet) : KunneIkkeIverksette
    object FantIkkeBehandling : KunneIkkeIverksette
    object KunneIkkeGenerereVedtaksbrev : KunneIkkeIverksette
    object AvkortingErUfullstendig : KunneIkkeIverksette
    object HarBlittAnnullertAvEnAnnen : KunneIkkeIverksette
    object HarAlleredeBlittAvkortetAvEnAnnen : KunneIkkeIverksette
    object KunneIkkeOpprettePlanlagtKontrollsamtale : KunneIkkeIverksette
    object LagringFeilet : KunneIkkeIverksette
}

sealed class KunneIkkeBeregne {
    data class UgyldigTilstand(
        val fra: KClass<out Søknadsbehandling>,
        val til: KClass<out Søknadsbehandling.Beregnet> = Søknadsbehandling.Beregnet::class,
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
    data class KunneIkkeSimulere(val feil: SimuleringFeilet) : KunneIkkeSimulereBehandling()
    data class UgyldigTilstand(
        val fra: KClass<out Søknadsbehandling>,
        val til: KClass<out Søknadsbehandling.Simulert> = Søknadsbehandling.Simulert::class,
    ) : KunneIkkeSimulereBehandling()
}
