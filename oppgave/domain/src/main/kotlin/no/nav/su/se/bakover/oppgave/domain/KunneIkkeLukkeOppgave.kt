package no.nav.su.se.bakover.oppgave.domain

import no.nav.su.se.bakover.common.domain.oppgave.OppgaveId

sealed interface KunneIkkeLukkeOppgave {
    val oppgaveId: OppgaveId

    fun feilPgaAlleredeFerdigstilt(): Boolean = when (this) {
        is FeilVedOppdateringAvOppgave -> when (this.originalFeil) {
            is KunneIkkeOppdatereOppgave.OppgaveErFerdigstilt -> true
            else -> false
        }

        else -> false
    }

    data class FeilVedHentingAvOppgave(override val oppgaveId: OppgaveId) : KunneIkkeLukkeOppgave {
        override fun toSikkerloggString() = toString()
        override fun toString(): String = "FeilVedHentingAvOppgave(oppaveId=$oppgaveId)"
    }

    data class FeilVedOppdateringAvOppgave(
        override val oppgaveId: OppgaveId,
        val originalFeil: KunneIkkeOppdatereOppgave,
    ) : KunneIkkeLukkeOppgave {
        override fun toSikkerloggString() =
            "FeilVedOppdateringAvOppgave(oppaveId=$oppgaveId,originalFeil=${originalFeil.toSikkerloggString()})"

        override fun toString() =
            "FeilVedOppdateringAvOppgave(oppaveId=$oppgaveId,KunneIkkeOppdatereOppgave=$originalFeil)"
    }

    data class FeilVedHentingAvToken(override val oppgaveId: OppgaveId) : KunneIkkeLukkeOppgave {
        override fun toSikkerloggString() = toString()
        override fun toString(): String = "FeilVedHentingAvToken(oppaveId=$oppgaveId)"
    }

    override fun toString(): String
    fun toSikkerloggString(): String
}
