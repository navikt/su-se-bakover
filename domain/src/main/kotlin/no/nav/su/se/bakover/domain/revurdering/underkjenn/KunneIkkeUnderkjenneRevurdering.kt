package no.nav.su.se.bakover.domain.revurdering.underkjenn

import no.nav.su.se.bakover.domain.revurdering.Revurdering
import kotlin.reflect.KClass

sealed interface KunneIkkeUnderkjenneRevurdering {
    data object FantIkkeRevurdering : KunneIkkeUnderkjenneRevurdering
    data object FantIkkeAktørId : KunneIkkeUnderkjenneRevurdering
    data class UgyldigTilstand(
        val fra: KClass<out Revurdering>,
        val til: KClass<out Revurdering>,
    ) : KunneIkkeUnderkjenneRevurdering

    data object KunneIkkeOppretteOppgave : KunneIkkeUnderkjenneRevurdering
    data object SaksbehandlerOgAttestantKanIkkeVæreSammePerson : KunneIkkeUnderkjenneRevurdering
}
