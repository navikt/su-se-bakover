package no.nav.su.se.bakover.client.oppgave

import java.time.LocalDate
import java.time.ZonedDateTime

internal data class OppgaveResponse(
    val id: Long,
    val tildeltEnhetsnr: String,
    val journalpostId: String?,
    val behandlesAvApplikasjon: String,
    val saksreferanse: String,
    val aktoerId: String,
    val tema: String,
    val behandlingstema: String?,
    val oppgavetype: String,
    val behandlingstype: String,
    val versjon: Int,
    val opprettetAv: String,
    val prioritet: String,
    val status: String,
    val metadata: Any,
    val fristFerdigstillelse: LocalDate,
    val aktivDato: LocalDate,
    val opprettetTidspunkt: ZonedDateTime
)
