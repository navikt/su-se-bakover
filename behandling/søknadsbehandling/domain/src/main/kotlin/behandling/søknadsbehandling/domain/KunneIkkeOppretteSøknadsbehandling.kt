package behandling.søknadsbehandling.domain

sealed interface KunneIkkeOppretteSøknadsbehandling {
    data object ErLukket : KunneIkkeOppretteSøknadsbehandling
    data object ManglerOppgave : KunneIkkeOppretteSøknadsbehandling
    data object HarÅpenSøknadsbehandling : KunneIkkeOppretteSøknadsbehandling
}
