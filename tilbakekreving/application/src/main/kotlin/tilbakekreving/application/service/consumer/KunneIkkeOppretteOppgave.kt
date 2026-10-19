package tilbakekreving.application.service.consumer

sealed interface KunneIkkeOppretteOppgave {
    data object FeilVedOpprettelseAvOppgave : KunneIkkeOppretteOppgave
}
