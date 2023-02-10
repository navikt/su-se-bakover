package no.nav.su.se.bakover.domain.revurdering.vilkår.utenlandsopphold

import no.nav.su.se.bakover.domain.revurdering.Revurdering
import kotlin.reflect.KClass

sealed class KunneIkkeLeggeTilUtenlandsopphold {
    object FantIkkeBehandling : KunneIkkeLeggeTilUtenlandsopphold()
    object OverlappendeVurderingsperioder : KunneIkkeLeggeTilUtenlandsopphold()
    object PeriodeForGrunnlagOgVurderingErForskjellig : KunneIkkeLeggeTilUtenlandsopphold()
    object AlleVurderingsperioderMåHaSammeResultat : KunneIkkeLeggeTilUtenlandsopphold()
    object MåVurdereHelePerioden : KunneIkkeLeggeTilUtenlandsopphold()
    object VurderingsperiodeUtenforBehandlingsperiode : KunneIkkeLeggeTilUtenlandsopphold()
    data class UgyldigTilstand(
        val fra: KClass<out Revurdering>,
        val til: KClass<out Revurdering>,
    ) : KunneIkkeLeggeTilUtenlandsopphold()
}
