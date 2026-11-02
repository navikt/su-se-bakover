package dokument.domain

import arrow.core.Either
import dokument.domain.generer.bygger.PdfBestilling

interface PensjonPdfByggerKlient {
    fun lagPdf(bestilling: PdfBestilling): Either<PdfByggerKlientFeil, ByteArray>
}
object PdfByggerKlientFeil
