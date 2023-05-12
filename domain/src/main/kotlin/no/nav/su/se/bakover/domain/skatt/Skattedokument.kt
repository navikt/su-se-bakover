package no.nav.su.se.bakover.domain.skatt

import no.nav.su.se.bakover.common.application.journal.JournalpostId
import java.util.UUID

interface SkattedokumentFelter {
    val id: UUID
    val søkersSkatteId: UUID
    val epsSkatteId: UUID
    val sakid: UUID
    val vedtakid: UUID
    val generertDokument: ByteArray
    val originalJson: String
}

sealed interface Skattedokument : SkattedokumentFelter {

    fun tilJournalført(journalpostId: JournalpostId): Journalført

    data class Generert(
        override val id: UUID,
        override val søkersSkatteId: UUID,
        override val epsSkatteId: UUID,
        override val sakid: UUID,
        override val vedtakid: UUID,
        override val generertDokument: ByteArray,
        override val originalJson: String
    ) : Skattedokument {
        override fun tilJournalført(journalpostId: JournalpostId): Journalført = Journalført(this, journalpostId)
    }

    data class Journalført(
        val generert: Generert,
        val journalpostid: JournalpostId
    ) : Skattedokument by generert {
        override fun tilJournalført(journalpostId: JournalpostId): Journalført = this
    }
}
