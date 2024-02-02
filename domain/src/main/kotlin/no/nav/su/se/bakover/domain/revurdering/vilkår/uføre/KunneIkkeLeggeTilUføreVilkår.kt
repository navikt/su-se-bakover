package no.nav.su.se.bakover.domain.revurdering.vilkår.uføre

import no.nav.su.se.bakover.domain.revurdering.Revurdering
import no.nav.su.se.bakover.domain.vilkår.uføre.LeggTilUførevurderingerRequest
import kotlin.reflect.KClass

sealed interface KunneIkkeLeggeTilUføreVilkår {
    data object FantIkkeBehandling : KunneIkkeLeggeTilUføreVilkår
    data object VurderingsperiodenKanIkkeVæreUtenforBehandlingsperioden : KunneIkkeLeggeTilUføreVilkår
    data class UgyldigTilstand(
        val fra: KClass<out Revurdering>,
        val til: KClass<out Revurdering>,
    ) : KunneIkkeLeggeTilUføreVilkår

    data class UgyldigInput(
        val originalFeil: LeggTilUførevurderingerRequest.UgyldigUførevurdering,
    ) : KunneIkkeLeggeTilUføreVilkår
}
