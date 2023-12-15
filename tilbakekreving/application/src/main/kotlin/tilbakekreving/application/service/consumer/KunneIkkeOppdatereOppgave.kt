package tilbakekreving.application.service.consumer

sealed interface KunneIkkeOppdatereOppgave {
    data class FeilVedLukkingAvOppgave(val originalFeil: no.nav.su.se.bakover.oppgave.domain.KunneIkkeOppdatereOppgave) : KunneIkkeOppdatereOppgave
}
