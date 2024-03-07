package no.nav.su.se.bakover.domain.revurdering.vilkår.bosituasjon

import vilkår.vurderinger.domain.Konsistensproblem

/**
 * Søknadsbehandling har fått sin egen type, så denne eies nå alene av revurdering og kan refaktoreres deretter.
 */
sealed interface KunneIkkeLeggeTilBosituasjongrunnlag {
    data object FantIkkeBehandling : KunneIkkeLeggeTilBosituasjongrunnlag
    data object UgyldigData : KunneIkkeLeggeTilBosituasjongrunnlag
    data object KunneIkkeSlåOppEPS : KunneIkkeLeggeTilBosituasjongrunnlag
    data object EpsAlderErNull : KunneIkkeLeggeTilBosituasjongrunnlag
    data class Konsistenssjekk(
        val feil: Konsistensproblem.Bosituasjon,
    ) : KunneIkkeLeggeTilBosituasjongrunnlag

    data class KunneIkkeLeggeTilBosituasjon(
        val feil: no.nav.su.se.bakover.domain.vilkår.bosituasjon.KunneIkkeLeggeTilBosituasjon,
    ) : KunneIkkeLeggeTilBosituasjongrunnlag

    // TODO jah - Klassen er delt mellom søknadsbehandling og revurdering, men disse feilene blir bare brukt av søknadsbehandling
    data class KunneIkkeLeggeTilGrunnlag(
        val feil: no.nav.su.se.bakover.domain.søknadsbehandling.grunnlag.KunneIkkeLeggeTilGrunnlag,
    ) : KunneIkkeLeggeTilBosituasjongrunnlag
}
