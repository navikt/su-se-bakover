package no.nav.su.se.bakover.client.oppgave

internal data class OppgaveResponseMedMetadata(
    val oppgaveResponse: OppgaveResponse,
    val jsonRequest: String?,
    val jsonResponse: String,
)
