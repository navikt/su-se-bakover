package no.nav.su.se.bakover.client.pdf

import arrow.core.Either
import dokument.domain.brev.PdfInnhold
import no.nav.su.se.bakover.client.ClientError
import no.nav.su.se.bakover.common.domain.PdfA
import no.nav.su.se.bakover.domain.søknad.SøknadPdfInnhold

interface PdfGenerator {
    fun genererPdf(søknadPdfInnhold: SøknadPdfInnhold): Either<ClientError, PdfA>
    fun genererPdf(pdfInnhold: PdfInnhold): Either<KunneIkkeGenererePdf, PdfA>
}

data object KunneIkkeGenererePdf
