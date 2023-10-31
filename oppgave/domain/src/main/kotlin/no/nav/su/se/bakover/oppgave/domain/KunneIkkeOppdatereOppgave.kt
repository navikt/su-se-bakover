package no.nav.su.se.bakover.oppgave.domain

sealed interface KunneIkkeOppdatereOppgave {
    data object FeilVedHentingAvOppgave : KunneIkkeOppdatereOppgave
    data object OppgaveErFerdigstilt : KunneIkkeOppdatereOppgave
    data object FeilVedRequest : KunneIkkeOppdatereOppgave
    data object FeilVedHentingAvToken : KunneIkkeOppdatereOppgave
}
