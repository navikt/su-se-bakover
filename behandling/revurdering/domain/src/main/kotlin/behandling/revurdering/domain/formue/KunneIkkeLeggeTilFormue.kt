package behandling.revurdering.domain.formue

import no.nav.su.se.bakover.behandling.Stønadsbehandling
import vilkår.vurderinger.domain.Konsistensproblem

sealed interface KunneIkkeLeggeTilFormue {
    data class UgyldigTilstand(
        // TODO jah: Bytt til Revurdering når vi har flyttet alt over
        val fra: kotlin.reflect.KClass<out Stønadsbehandling>,
        val til: kotlin.reflect.KClass<out Stønadsbehandling>,
    ) : KunneIkkeLeggeTilFormue

    data class Konsistenssjekk(val feil: Konsistensproblem.BosituasjonOgFormue) : KunneIkkeLeggeTilFormue
}
