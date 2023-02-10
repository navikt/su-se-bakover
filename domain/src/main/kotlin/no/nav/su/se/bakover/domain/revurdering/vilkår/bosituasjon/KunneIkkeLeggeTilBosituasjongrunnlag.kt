package no.nav.su.se.bakover.domain.revurdering.vilkår.bosituasjon

import no.nav.su.se.bakover.domain.grunnlag.Konsistensproblem
import no.nav.su.se.bakover.domain.revurdering.Revurdering

sealed class KunneIkkeLeggeTilBosituasjongrunnlag {
    object FantIkkeBehandling : KunneIkkeLeggeTilBosituasjongrunnlag()
    object UgyldigData : KunneIkkeLeggeTilBosituasjongrunnlag()
    object KunneIkkeSlåOppEPS : KunneIkkeLeggeTilBosituasjongrunnlag()
    object EpsAlderErNull : KunneIkkeLeggeTilBosituasjongrunnlag()
    data class Konsistenssjekk(val feil: Konsistensproblem.Bosituasjon) : KunneIkkeLeggeTilBosituasjongrunnlag()
    data class KunneIkkeLeggeTilBosituasjon(val feil: Revurdering.KunneIkkeLeggeTilBosituasjon) :
        KunneIkkeLeggeTilBosituasjongrunnlag()
}
