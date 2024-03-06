package no.nav.su.se.bakover.domain.vilkår.bosituasjon

import no.nav.su.se.bakover.behandling.Stønadsbehandling
import no.nav.su.se.bakover.domain.revurdering.KunneIkkeLeggeTilFormue
import vilkår.vurderinger.domain.Konsistensproblem
import vilkår.vurderinger.domain.KunneIkkeLageGrunnlagsdata
import kotlin.reflect.KClass

sealed interface KunneIkkeLeggeTilBosituasjon {
    data class Valideringsfeil(val feil: KunneIkkeLageGrunnlagsdata) : KunneIkkeLeggeTilBosituasjon
    data class UgyldigTilstand(
        val fra: KClass<out Stønadsbehandling>,
        val til: KClass<out Stønadsbehandling>,
    ) : KunneIkkeLeggeTilBosituasjon

    data class Konsistenssjekk(val feil: Konsistensproblem.Bosituasjon) : KunneIkkeLeggeTilBosituasjon
    data class KunneIkkeOppdatereFormue(val feil: KunneIkkeLeggeTilFormue) : KunneIkkeLeggeTilBosituasjon
    data object PerioderMangler : KunneIkkeLeggeTilBosituasjon
}
