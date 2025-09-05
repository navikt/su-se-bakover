package no.nav.su.se.bakover.domain.søknadsbehandling.retur

import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import kotlin.reflect.KClass

sealed interface KunneIkkeReturnereSøknadsbehandling {
    data object FantIkkeBehandling : KunneIkkeReturnereSøknadsbehandling
    data object KunneIkkeOppretteOppgave : KunneIkkeReturnereSøknadsbehandling
    data object FantIkkeAktørId : KunneIkkeReturnereSøknadsbehandling

    data class UgyldigTilstand(
        val fra: KClass<out Søknadsbehandling>,
    ) : KunneIkkeReturnereSøknadsbehandling {
    }

    data object FeilSaksbehandler : KunneIkkeReturnereSøknadsbehandling
}
