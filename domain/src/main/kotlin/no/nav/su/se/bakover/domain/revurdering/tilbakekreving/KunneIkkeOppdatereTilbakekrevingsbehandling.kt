package no.nav.su.se.bakover.domain.revurdering.tilbakekreving

import no.nav.su.se.bakover.domain.revurdering.Revurdering
import no.nav.su.se.bakover.domain.revurdering.SimulertRevurdering
import kotlin.reflect.KClass

sealed interface KunneIkkeOppdatereTilbakekrevingsbehandling {
    data class UgyldigTilstand(
        val fra: KClass<out Revurdering>,
        val til: KClass<out Revurdering> = SimulertRevurdering::class,
    ) : KunneIkkeOppdatereTilbakekrevingsbehandling
}
