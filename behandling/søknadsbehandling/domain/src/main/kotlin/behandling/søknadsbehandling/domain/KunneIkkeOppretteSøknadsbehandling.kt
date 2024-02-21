package behandling.søknadsbehandling.domain

sealed interface KunneIkkeOppretteSøknadsbehandling {
    data object ErLukket : KunneIkkeOppretteSøknadsbehandling
    data object ManglerOppgave : KunneIkkeOppretteSøknadsbehandling
    data object KanIkkeStarteNyBehandling : KunneIkkeOppretteSøknadsbehandling
    data object FeilVedOpprettingAvOppgave : KunneIkkeOppretteSøknadsbehandling
}
