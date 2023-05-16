package no.nav.su.se.bakover.domain.skatt

import no.nav.su.se.bakover.common.application.journal.JournalpostId
import java.util.UUID

sealed interface Skattedokument {
    val id: UUID
    val søkersSkatteId: UUID
    val epsSkatteId: UUID?
    val sakid: UUID
    val vedtakid: UUID
    val generertDokument: ByteArray
    val dokumentJson: String
    val journalpostid: JournalpostId?

    data class Generert(
        override val søkersSkatteId: UUID,
        override val epsSkatteId: UUID?,
        override val sakid: UUID,
        override val vedtakid: UUID,
        override val id: UUID,
        override val generertDokument: ByteArray,
        override val dokumentJson: String,
        override val journalpostid: JournalpostId? = null,
    ) : Skattedokument {
        fun tilJournalført(journalpostId: JournalpostId): Journalført = Journalført(this, journalpostId)
    }

    data class Journalført(
        val generert: Generert,
        override val journalpostid: JournalpostId,
    ) : Skattedokument by generert
}
