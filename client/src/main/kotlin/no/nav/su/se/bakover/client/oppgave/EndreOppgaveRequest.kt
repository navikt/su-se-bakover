package no.nav.su.se.bakover.client.oppgave

internal data class EndreOppgaveRequest(
    val beskrivelse: String,
    val oppgavetype: String,
    val status: String,
)
