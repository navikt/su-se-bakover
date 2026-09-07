package dokument.domain.journalføring

import no.nav.su.se.bakover.common.domain.PdfA

data class JournalpostVedlegg(
    val filnavn: String,
    val pdf: PdfA,
)
