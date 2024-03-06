package no.nav.su.se.bakover.domain.revurdering

import vilkår.vurderinger.domain.Konsistensproblem

/**
 * Søknadsbehandling har fått sin egen type, så denne eies nå alene av revurdering og kan refaktoreres deretter.
 */
sealed interface KunneIkkeLeggeTilFormue {
    data class UgyldigTilstand(
        val fra: kotlin.reflect.KClass<out Revurdering>,
        val til: kotlin.reflect.KClass<out Revurdering> = OpprettetRevurdering::class,
    ) : KunneIkkeLeggeTilFormue

    data class Konsistenssjekk(val feil: Konsistensproblem.BosituasjonOgFormue) : KunneIkkeLeggeTilFormue
}
