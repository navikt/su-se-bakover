package no.nav.su.se.bakover.domain.søknadsbehandling.retur

import no.nav.su.se.bakover.domain.søknadsbehandling.ReturnerSøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.UnderkjentSøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.underkjenn.KunneIkkeUnderkjenneSøknadsbehandling
import kotlin.reflect.KClass

sealed interface KunneIkkeReturnereSøknadsbehandling {
    data object FantIkkeBehandling : KunneIkkeReturnereSøknadsbehandling
    data object AttestantOgSaksbehandlerKanIkkeVæreSammePerson : KunneIkkeReturnereSøknadsbehandling
    data object KunneIkkeOppretteOppgave : KunneIkkeReturnereSøknadsbehandling
    data object FantIkkeAktørId : KunneIkkeReturnereSøknadsbehandling

    data class UgyldigTilstand(
        val fra: KClass<out Søknadsbehandling>,
    ) : KunneIkkeReturnereSøknadsbehandling {
        val til: KClass<out ReturnerSøknadsbehandling> = ReturnerSøknadsbehandling::class
    }

    data object FeilSaksbehandler : KunneIkkeReturnereSøknadsbehandling
}
