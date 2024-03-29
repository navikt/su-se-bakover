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
}
