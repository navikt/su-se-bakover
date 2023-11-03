package tilbakekreving.application.service.consumer

sealed interface KunneIkkeLukkeOppgave {
    data object FeilVedLukkingAvOppgave : KunneIkkeLukkeOppgave
}
