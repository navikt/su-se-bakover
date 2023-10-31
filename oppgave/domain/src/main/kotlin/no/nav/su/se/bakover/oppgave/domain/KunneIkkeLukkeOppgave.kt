package no.nav.su.se.bakover.oppgave.domain

import no.nav.su.se.bakover.common.domain.oppgave.OppgaveId

sealed interface KunneIkkeLukkeOppgave {
    val oppgaveId: OppgaveId

    data class FeilVedHentingAvOppgave(override val oppgaveId: OppgaveId) : KunneIkkeLukkeOppgave
    data class FeilVedOppdateringAvOppgave(
        override val oppgaveId: OppgaveId,
        val originalFeil: KunneIkkeOppdatereOppgave,
    ) : KunneIkkeLukkeOppgave

    data class FeilVedHentingAvToken(override val oppgaveId: OppgaveId) : KunneIkkeLukkeOppgave

    fun erOppgaveFerdigstilt(): Boolean {
        return when (this) {
            is FeilVedHentingAvOppgave -> false
            is FeilVedHentingAvToken -> false
            is FeilVedOppdateringAvOppgave -> when (this.originalFeil) {
                KunneIkkeOppdatereOppgave.FeilVedHentingAvOppgave -> false
                KunneIkkeOppdatereOppgave.FeilVedHentingAvToken -> false
                KunneIkkeOppdatereOppgave.FeilVedRequest -> false
                KunneIkkeOppdatereOppgave.OppgaveErFerdigstilt -> true
            }
        }
    }
}
