package no.nav.su.se.bakover.client.oppgave

import no.nav.su.se.bakover.common.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.oppgave.OppgaveSøkeResultat
import no.nav.su.se.bakover.oppgave.domain.Oppgave
import java.time.LocalDate
import java.time.ZonedDateTime

internal data class OppgaveSøkResponse(
    val oppgaver: List<OppgaveSøkeResultat>,
)

internal data class OppdatertOppgaveResponse(
    val id: Long,
    val versjon: Int,
    val status: String,
)

internal data class OppgaveResponse(
    val id: Long,
    val tildeltEnhetsnr: String,
    val journalpostId: String?,
    val saksreferanse: String?,
    val aktoerId: String?,
    val beskrivelse: String?,
    val tema: String?,
    val behandlingstema: String?,
    val oppgavetype: String,
    val behandlingstype: String?,
    val versjon: Int,
    val opprettetAv: String,
    val prioritet: String,
    val status: String,
    val metadata: Any?,
    val fristFerdigstillelse: LocalDate?,
    val aktivDato: LocalDate,
    val opprettetTidspunkt: ZonedDateTime,
) {
    fun getOppgaveId() = OppgaveId(id.toString())
    fun erFerdigstilt(): Boolean = status == "FERDIGSTILT"

    fun toDomain(): Oppgave {
        return Oppgave(
            id = OppgaveId(id.toString()),
            versjon = versjon,
            status = toDomainStatus(),
        )
    }

    fun toDomainStatus(): Oppgave.Oppgavestatus {
        return when (status) {
            "OPPRETTET" -> Oppgave.Oppgavestatus.Opprettet
            "AAPNET" -> Oppgave.Oppgavestatus.Åpnet
            "UNDER_BEHANDLING" -> Oppgave.Oppgavestatus.UnderBehandling
            "FERDIGSTILT" -> Oppgave.Oppgavestatus.Ferdigstilt
            "FEILREGISTRERT" -> Oppgave.Oppgavestatus.Feilregistert
            else -> throw RuntimeException("Ukjent oppgavestatus $status")
        }
    }
}
