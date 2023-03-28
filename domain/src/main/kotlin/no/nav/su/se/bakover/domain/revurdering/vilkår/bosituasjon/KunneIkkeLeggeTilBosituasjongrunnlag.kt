package no.nav.su.se.bakover.domain.revurdering.vilkår.bosituasjon

import no.nav.su.se.bakover.domain.grunnlag.Konsistensproblem

sealed class KunneIkkeLeggeTilBosituasjongrunnlag {
    object FantIkkeBehandling : KunneIkkeLeggeTilBosituasjongrunnlag()
    object UgyldigData : KunneIkkeLeggeTilBosituasjongrunnlag()
    object KunneIkkeSlåOppEPS : KunneIkkeLeggeTilBosituasjongrunnlag()
    object EpsAlderErNull : KunneIkkeLeggeTilBosituasjongrunnlag()
    data class Konsistenssjekk(val feil: Konsistensproblem.Bosituasjon) : KunneIkkeLeggeTilBosituasjongrunnlag()
    data class KunneIkkeLeggeTilBosituasjon(val feil: no.nav.su.se.bakover.domain.vilkår.bosituasjon.KunneIkkeLeggeTilBosituasjon) :
        KunneIkkeLeggeTilBosituasjongrunnlag()

    // TODO - Klassen er delt mellom søknadsbehandling og revurdering, men disse feilene blir bare brukt av søknadsbehandling
    // På sikt så vil vi at disse skal fjernes, og forhåpentligvis ufullstendig/fullstendig.
    data class KunneIkkeLeggeTilGrunnlag(val feil: no.nav.su.se.bakover.domain.søknadsbehandling.KunneIkkeLeggeTilGrunnlag) : KunneIkkeLeggeTilBosituasjongrunnlag()
}
