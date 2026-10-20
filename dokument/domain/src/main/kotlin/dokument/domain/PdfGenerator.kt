package dokument.domain

import arrow.core.Either
import dokument.domain.pdf.PdfInnhold
import no.nav.brev.brevbaker.document.DocumentPDFRequest
import no.nav.brev.brevbaker.pdfbygger.api.LetterPDFRequest
import no.nav.su.se.bakover.common.domain.PdfA

interface PdfGenerator {
    fun genererPdf(pdfInnhold: PdfInnhold): Either<KunneIkkeGenererePdf, PdfA>
}

interface PensjonPdfByggerKlient {
    fun lagPdf(bestilling: PdfBestilling, korrelasjonsId: String?): ByteArray
}

sealed interface PdfBestilling {
    data class Brev(val request: LetterPDFRequest) : PdfBestilling
    data class Dokument(val request: DocumentPDFRequest) : PdfBestilling
}

data object KunneIkkeGenererePdf
