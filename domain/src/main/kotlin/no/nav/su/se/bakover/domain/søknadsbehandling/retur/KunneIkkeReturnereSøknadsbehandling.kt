package no.nav.su.se.bakover.domain.søknadsbehandling.retur

sealed interface KunneIkkeReturnereSøknadsbehandling {
    data object FantIkkeBehandling : KunneIkkeReturnereSøknadsbehandling
    data object FeilSaksbehandler : KunneIkkeReturnereSøknadsbehandling
}
