package no.nav.su.se.bakover.domain.journalpost

import no.nav.su.se.bakover.common.journal.JournalpostId

data class Journalpost(
    val id: JournalpostId,
    val tittel: String,
)

enum class JournalpostTema {
    SUP,
}

enum class JournalpostStatus {
    JOURNALFOERT,
    FERDIGSTILT,
}

enum class JournalpostType {
    INNKOMMENDE_DOKUMENT,
}
