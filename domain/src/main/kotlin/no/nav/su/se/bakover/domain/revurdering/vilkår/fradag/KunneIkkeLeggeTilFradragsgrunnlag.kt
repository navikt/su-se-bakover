package no.nav.su.se.bakover.domain.revurdering.vilkår.fradag

import no.nav.su.se.bakover.domain.revurdering.Revurdering
import vilkår.vurderinger.domain.KunneIkkeLageGrunnlagsdata
import kotlin.reflect.KClass

sealed interface KunneIkkeLeggeTilFradragsgrunnlag {
    data object FantIkkeBehandling : KunneIkkeLeggeTilFradragsgrunnlag
    data class UgyldigTilstand(
        val fra: KClass<out Revurdering>,
        val til: KClass<out Revurdering>,
    ) : KunneIkkeLeggeTilFradragsgrunnlag

    data class KunneIkkeEndreFradragsgrunnlag(val feil: KunneIkkeLageGrunnlagsdata) : KunneIkkeLeggeTilFradragsgrunnlag
    data object FradrageneMåSlåsSammen : KunneIkkeLeggeTilFradragsgrunnlag
}
