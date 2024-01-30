package no.nav.su.se.bakover.domain.revurdering.vilkår.utenlandsopphold

import no.nav.su.se.bakover.domain.revurdering.Revurdering
import kotlin.reflect.KClass

sealed interface KunneIkkeLeggeTilUtenlandsopphold {
    data object FantIkkeBehandling : KunneIkkeLeggeTilUtenlandsopphold
    data object OverlappendeVurderingsperioder : KunneIkkeLeggeTilUtenlandsopphold
    data object PeriodeForGrunnlagOgVurderingErForskjellig : KunneIkkeLeggeTilUtenlandsopphold
    data object AlleVurderingsperioderMåHaSammeResultat : KunneIkkeLeggeTilUtenlandsopphold
    data object MåVurdereHelePerioden : KunneIkkeLeggeTilUtenlandsopphold
    data object VurderingsperiodeUtenforBehandlingsperiode : KunneIkkeLeggeTilUtenlandsopphold
    data class UgyldigTilstand(
        val fra: KClass<out Revurdering>,
        val til: KClass<out Revurdering>,
    ) : KunneIkkeLeggeTilUtenlandsopphold
}
