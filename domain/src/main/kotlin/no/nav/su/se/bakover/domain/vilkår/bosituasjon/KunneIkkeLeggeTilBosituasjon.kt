package no.nav.su.se.bakover.domain.vilkår.bosituasjon

import no.nav.su.se.bakover.domain.behandling.Behandling
import no.nav.su.se.bakover.domain.grunnlag.Konsistensproblem
import no.nav.su.se.bakover.domain.grunnlag.KunneIkkeLageGrunnlagsdata
import no.nav.su.se.bakover.domain.revurdering.Revurdering
import kotlin.reflect.KClass

sealed class KunneIkkeLeggeTilBosituasjon {
    data class Valideringsfeil(val feil: KunneIkkeLageGrunnlagsdata) : KunneIkkeLeggeTilBosituasjon()
    data class UgyldigTilstand(val fra: KClass<out Behandling>, val til: KClass<out Behandling>) :
        KunneIkkeLeggeTilBosituasjon()

    data class Konsistenssjekk(val feil: Konsistensproblem.Bosituasjon) : KunneIkkeLeggeTilBosituasjon()
    data class KunneIkkeOppdatereFormue(val feil: Revurdering.KunneIkkeLeggeTilFormue) : KunneIkkeLeggeTilBosituasjon()
    object PerioderMangler : KunneIkkeLeggeTilBosituasjon()
}
