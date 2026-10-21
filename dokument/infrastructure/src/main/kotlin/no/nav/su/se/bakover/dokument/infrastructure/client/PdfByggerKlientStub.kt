package no.nav.su.se.bakover.dokument.infrastructure.client

import arrow.core.Either
import arrow.core.right
import dokument.domain.PdfBestilling
import dokument.domain.PdfByggerKlientFeil
import dokument.domain.PensjonPdfByggerKlient

class PdfByggerKlientStub : PensjonPdfByggerKlient {
    private val pdfBytes = requireNotNull(javaClass.classLoader.getResourceAsStream("Foersteside.pdf")).use { it.readAllBytes() }

    override fun lagPdf(bestilling: PdfBestilling): Either<PdfByggerKlientFeil, ByteArray> {
        return pdfBytes.right()
    }
}
