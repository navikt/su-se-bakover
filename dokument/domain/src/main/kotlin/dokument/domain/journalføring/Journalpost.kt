package dokument.domain.journalføring

import no.nav.su.se.bakover.common.journal.JournalpostId

/**
 * Vår representasjon av en mottatt journalpost
 *
 * Se [no.nav.su.se.bakover.client.journalpost.Journalpost] for modell som vi henter fra Joark
 */
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
