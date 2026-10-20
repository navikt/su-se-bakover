package dokument.domain

import arrow.core.Either
import dokument.domain.pdf.PdfInnhold
import no.nav.brev.brevbaker.document.DocumentPDFRequest
import no.nav.brev.brevbaker.pdfbygger.api.LetterPDFRequest
import no.nav.su.se.bakover.common.domain.PdfA

interface PdfGenerator {
    fun genererPdf(pdfInnhold: PdfInnhold): Either<KunneIkkeGenererePdf, PdfA>
}

data object KunneIkkeGenererePdf
