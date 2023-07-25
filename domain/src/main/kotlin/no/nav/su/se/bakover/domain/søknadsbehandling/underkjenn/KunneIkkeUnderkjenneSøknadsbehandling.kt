package no.nav.su.se.bakover.domain.søknadsbehandling.underkjenn

import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.UnderkjentSøknadsbehandling
import kotlin.reflect.KClass

sealed interface KunneIkkeUnderkjenneSøknadsbehandling {
    data object FantIkkeBehandling : KunneIkkeUnderkjenneSøknadsbehandling
    data object AttestantOgSaksbehandlerKanIkkeVæreSammePerson : KunneIkkeUnderkjenneSøknadsbehandling
    data object KunneIkkeOppretteOppgave : KunneIkkeUnderkjenneSøknadsbehandling
    data object FantIkkeAktørId : KunneIkkeUnderkjenneSøknadsbehandling

    data class UgyldigTilstand(
        val fra: KClass<out Søknadsbehandling>,
    ) : KunneIkkeUnderkjenneSøknadsbehandling {
        val til: KClass<out UnderkjentSøknadsbehandling> = UnderkjentSøknadsbehandling::class
    }
}
