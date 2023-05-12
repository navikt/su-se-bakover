package no.nav.su.se.bakover.client.pdf

import arrow.core.Either
import no.nav.su.se.bakover.client.ClientError
import no.nav.su.se.bakover.domain.brev.PdfInnhold
import no.nav.su.se.bakover.domain.søknad.SøknadPdfInnhold

interface PdfGenerator {
    fun genererPdf(søknadPdfInnhold: SøknadPdfInnhold): Either<ClientError, ByteArray>
    fun genererPdf(pdfInnhold: PdfInnhold): Either<KunneIkkeGenererePdf, ByteArray>
    fun genererPdf(pdf: PdfDokument): Either<KunneIkkeGenererePdf, ByteArray>
}

object KunneIkkeGenererePdf
