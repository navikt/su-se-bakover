package no.nav.su.se.bakover.domain.revurdering

import no.nav.su.se.bakover.domain.revurdering.brev.BrevvalgRevurdering
import kotlin.reflect.KClass

/**
 * TODO jah: Kan extende Revurdering.kt når det har blitt et sealed interface.
 */
fun interface LeggTilVedtaksbrevvalg {
    // TODO jah: Kan returnere LeggTilVedtaksbrevvalg når det har blitt et sealed interface.
    fun leggTilBrevvalg(brevvalgRevurdering: BrevvalgRevurdering.Valgt): Revurdering
}

sealed interface KunneIkkeLeggeTilVedtaksbrevvalg {
    data class UgyldigTilstand(
        val tilstand: KClass<out Revurdering>,
    ) : KunneIkkeLeggeTilVedtaksbrevvalg
}
