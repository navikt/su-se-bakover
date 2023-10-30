package tilbakekreving.application.service.consumer

sealed interface KunneIkkeOppdatereOppgave {
    data object FeilVedLukkingAvOppgave : KunneIkkeOppdatereOppgave
}
