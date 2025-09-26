package behandling.søknadsbehandling.domain

sealed interface KunneIkkeStarteSøknadsbehandling {
    data object ErLukket : KunneIkkeStarteSøknadsbehandling
    data object ManglerOppgave : KunneIkkeStarteSøknadsbehandling
    data object FeilVedOpprettingAvOppgave : KunneIkkeStarteSøknadsbehandling
    data object FantIkkeBehandling : KunneIkkeStarteSøknadsbehandling
    data object BehandlingErAlleredePåbegynt : KunneIkkeStarteSøknadsbehandling
}
