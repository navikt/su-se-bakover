package no.nav.su.se.bakover.client.pdf

import arrow.core.Either
import no.nav.su.se.bakover.domain.brev.PdfTemplate

interface PdfGenerator {
    fun genererPdf(innholdJson: String, pdfTemplate: PdfTemplate): Either<KunneIkkeGenererePdf, ByteArray>
}

object KunneIkkeGenererePdf
