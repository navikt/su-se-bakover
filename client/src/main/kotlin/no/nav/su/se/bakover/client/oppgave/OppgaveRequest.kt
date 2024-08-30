package no.nav.su.se.bakover.client.oppgave

import java.time.LocalDate

internal data class OppgaveRequest(
    val journalpostId: String?,
    val saksreferanse: String,
    val personident: String,
    val tema: String,
    val beskrivelse: String,
    val oppgavetype: String,
    val behandlingstema: String?,
    val behandlingstype: String,
    val aktivDato: LocalDate,
    val fristFerdigstillelse: LocalDate,
    val prioritet: String,
    val tilordnetRessurs: String?,
    val tildeltEnhetsnr: String?,
) {
    init {
        if (tilordnetRessurs != null) {
            require(tildeltEnhetsnr != null) { "Tildelt enhetsnr må være satt når tilordnetRessurs er satt" }
        }
    }
}
