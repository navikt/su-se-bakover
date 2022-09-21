package no.nav.su.se.bakover.domain.journalpost

import no.nav.su.se.bakover.domain.Saksnummer

// TODO - Gjøre noe fine ting mellom denne og eksisterende journalpost
data class FerdigstiltJournalpost private constructor(
    private val tema: Tema,
    private val journalstatus: JournalpostStatus,
    private val journalpostType: JournalpostType,
    private val saksnummer: Saksnummer,
) {

    companion object {
        fun create(
            tema: Tema,
            journalstatus: JournalpostStatus,
            journalpostType: JournalpostType,
            saksnummer: Saksnummer,
        ): FerdigstiltJournalpost {
            return FerdigstiltJournalpost(tema, journalstatus, journalpostType, saksnummer)
        }
    }
}

enum class Tema {
    SUP,
}

enum class JournalpostStatus {
    JOURNALFOERT,
    ;
}

enum class JournalpostType(val value: String) {
    INNKOMMENDE_DOKUMENT("I"),
    ;

    companion object {
        fun fromString(value: String): JournalpostType {
            return values().find { it.value == value }
                ?: throw IllegalArgumentException("Journalposttypen $value er ikke støtte for")
        }
    }
}
