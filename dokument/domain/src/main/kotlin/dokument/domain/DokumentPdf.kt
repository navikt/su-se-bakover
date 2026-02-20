package dokument.domain

import no.nav.su.se.bakover.common.domain.PdfA
import java.util.UUID

data class DokumentPdf(
    val sakId: UUID,
    val tittel: String,
    val generertDokument: PdfA,
)
