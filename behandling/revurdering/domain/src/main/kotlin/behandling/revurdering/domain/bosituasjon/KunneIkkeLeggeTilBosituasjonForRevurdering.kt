package behandling.revurdering.domain.bosituasjon

import no.nav.su.se.bakover.behandling.Stønadsbehandling
import vilkår.vurderinger.domain.Konsistensproblem
import kotlin.reflect.KClass

sealed interface KunneIkkeLeggeTilBosituasjonForRevurdering {
    data class UgyldigTilstand(
        val fra: KClass<out Stønadsbehandling>,
        val til: KClass<out Stønadsbehandling>,
    ) : KunneIkkeLeggeTilBosituasjonForRevurdering

    data class Konsistenssjekk(val feil: Konsistensproblem.Bosituasjon) : KunneIkkeLeggeTilBosituasjonForRevurdering
    data class KunneIkkeOppdatereFormue(val feil: Konsistensproblem.BosituasjonOgFormue) : KunneIkkeLeggeTilBosituasjonForRevurdering
    data object PerioderMangler : KunneIkkeLeggeTilBosituasjonForRevurdering
}
