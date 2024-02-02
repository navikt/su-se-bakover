package no.nav.su.se.bakover.domain.revurdering.vilkår.formue

import no.nav.su.se.bakover.domain.revurdering.Revurdering
import no.nav.su.se.bakover.domain.vilkår.formue.LeggTilFormuevilkårRequest
import vilkår.vurderinger.domain.Konsistensproblem
import kotlin.reflect.KClass

sealed interface KunneIkkeLeggeTilFormuegrunnlag {
    data object FantIkkeRevurdering : KunneIkkeLeggeTilFormuegrunnlag
    data class UgyldigTilstand(
        val fra: KClass<out Revurdering>,
        val til: KClass<out Revurdering>,
    ) : KunneIkkeLeggeTilFormuegrunnlag

    data class Konsistenssjekk(val feil: Konsistensproblem.BosituasjonOgFormue) : KunneIkkeLeggeTilFormuegrunnlag

    data class KunneIkkeMappeTilDomenet(
        val feil: LeggTilFormuevilkårRequest.KunneIkkeMappeTilDomenet,
    ) : KunneIkkeLeggeTilFormuegrunnlag
}
