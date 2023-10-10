package no.nav.su.se.bakover.domain.skatt

import dokument.domain.SkattegrunnlagPdfTemplate
import no.nav.su.se.bakover.common.domain.PdfA
import no.nav.su.se.bakover.common.journal.JournalpostId
import no.nav.su.se.bakover.common.tid.Tidspunkt
import java.util.UUID

sealed interface Skattedokument {
    val id: UUID
    val søkersSkatteId: UUID
    val epsSkatteId: UUID?
    val sakid: UUID
    val vedtakid: UUID
    val generertDokument: PdfA
    val dokumentJson: String
    val journalpostid: JournalpostId?
    val skattedataHentet: Tidspunkt

    /**
     * til bruk for når man skal lage journalpost, da vi ikke har tatt vare på pdfinnholdet
     */
    val dokumentTittel: String get() = SkattegrunnlagPdfTemplate.tittel()
    fun tilJournalført(journalpostId: JournalpostId): Journalført

    data class Generert(
        override val id: UUID,
        override val søkersSkatteId: UUID,
        override val epsSkatteId: UUID?,
        override val sakid: UUID,
        override val vedtakid: UUID,
        override val generertDokument: PdfA,
        override val dokumentJson: String,
        override val skattedataHentet: Tidspunkt,
    ) : Skattedokument {
        override val journalpostid: JournalpostId? = null
        override fun tilJournalført(journalpostId: JournalpostId): Journalført = Journalført(this, journalpostId)
    }

    data class Journalført(
        val generert: Generert,
        override val journalpostid: JournalpostId,
    ) : Skattedokument by generert {
        override fun tilJournalført(journalpostId: JournalpostId): Journalført = this
    }
}
