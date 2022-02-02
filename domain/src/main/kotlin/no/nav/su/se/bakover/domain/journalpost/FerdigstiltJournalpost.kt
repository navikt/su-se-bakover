package no.nav.su.se.bakover.domain.journalpost

import no.nav.su.se.bakover.domain.Saksnummer

// TODO - Gjøre noe fine ting mellom denne og eksisterende journalpost
data class FerdigstiltJournalpost private constructor(
    private val tema: Tema,
    private val journalstatus: JournalpostStatus,
    private val saksnummer: Saksnummer,
) {

    companion object {
        fun create(
            tema: Tema,
            journalstatus: JournalpostStatus,
            saksnummer: Saksnummer,
        ): FerdigstiltJournalpost {
            return FerdigstiltJournalpost(tema, journalstatus, saksnummer)
        }
    }
}

enum class Tema {
    SUP
}

enum class JournalpostStatus {
    FERDIGSTILT
}
