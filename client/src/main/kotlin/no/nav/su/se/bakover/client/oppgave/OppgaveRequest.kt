package no.nav.su.se.bakover.client.oppgave

import no.nav.su.se.bakover.domain.oppgave.OppgavePrioritet
import java.time.LocalDate

internal data class OppgaveRequest(
    val journalpostId: String?,
    val saksreferanse: String,
    val personident: String,
    val behandlesAvApplikasjon: String?,
    val tema: String,
    val beskrivelse: String,
    val oppgavetype: String,
    val behandlingstema: String?,
    val behandlingstype: String,
    val aktivDato: LocalDate,
    val fristFerdigstillelse: LocalDate,
    val prioritet: OppgavePrioritet,
    val tilordnetRessurs: String?,
    val tildeltEnhetsnr: String?,
) {
    init {
        if (tilordnetRessurs != null) {
            require(tildeltEnhetsnr != null) { "Tildelt enhetsnr må være satt når tilordnetRessurs er satt" }
        }
    }
}
