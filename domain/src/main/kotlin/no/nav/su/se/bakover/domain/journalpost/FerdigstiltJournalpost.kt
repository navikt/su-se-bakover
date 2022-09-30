package no.nav.su.se.bakover.domain.journalpost

import no.nav.su.se.bakover.domain.Saksnummer

data class FerdigstiltJournalpost(
    val tema: JournalpostTema,
    val journalstatus: JournalpostStatus,
    val journalposttype: JournalpostType,
    val saksnummer: Saksnummer,
) {
    init {
        require(tema == JournalpostTema.SUP)
        require(journalposttype == JournalpostType.INNKOMMENDE_DOKUMENT)
        require(journalstatus == JournalpostStatus.JOURNALFOERT)
    }
}
