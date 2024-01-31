package no.nav.su.se.bakover.dokument.infrastructure.client

import arrow.core.Either
import dokument.domain.pdf.PdfInnhold
import no.nav.su.se.bakover.common.domain.PdfA

interface PdfGenerator {
    fun genererPdf(pdfInnhold: PdfInnhold): Either<KunneIkkeGenererePdf, PdfA>
}

data object KunneIkkeGenererePdf
