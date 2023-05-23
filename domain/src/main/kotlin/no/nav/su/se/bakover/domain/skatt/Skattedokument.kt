package no.nav.su.se.bakover.domain.skatt

import no.nav.su.se.bakover.common.journal.JournalpostId
import no.nav.su.se.bakover.domain.brev.SkattegrunnlagPdfTemplate
import java.util.UUID

sealed interface Skattedokument {
    val id: UUID
    val søkersSkatteId: UUID
    val epsSkatteId: UUID?
    val sakid: UUID
    val vedtakid: UUID
    val generertDokument: ByteArray
    val dokumentJson: String

    /**
     * til bruk for når man skal lage journalpost, da vi ikke har tatt vare på pdfinnholdet
     */
    val dokumentTittel: String get() = SkattegrunnlagPdfTemplate.tittel()

    data class Generert(
        override val id: UUID,
        override val søkersSkatteId: UUID,
        override val epsSkatteId: UUID?,
        override val sakid: UUID,
        override val vedtakid: UUID,
        override val generertDokument: ByteArray,
        override val dokumentJson: String,
    ) : Skattedokument {
        fun tilJournalført(journalpostId: JournalpostId): Journalført = Journalført(this, journalpostId)

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Generert

            if (id != other.id) return false
            if (søkersSkatteId != other.søkersSkatteId) return false
            if (epsSkatteId != other.epsSkatteId) return false
            if (sakid != other.sakid) return false
            if (vedtakid != other.vedtakid) return false
            if (!generertDokument.contentEquals(other.generertDokument)) return false
            return dokumentJson == other.dokumentJson
        }

        override fun hashCode(): Int {
            var result = id.hashCode()
            result = 31 * result + søkersSkatteId.hashCode()
            result = 31 * result + (epsSkatteId?.hashCode() ?: 0)
            result = 31 * result + sakid.hashCode()
            result = 31 * result + vedtakid.hashCode()
            result = 31 * result + generertDokument.contentHashCode()
            result = 31 * result + dokumentJson.hashCode()
            return result
        }
    }

    data class Journalført(
        val generert: Generert,
        val journalpostid: JournalpostId,
    ) : Skattedokument by generert
}
