package no.nav.su.se.bakover.client.pdf

import arrow.core.Either
import no.nav.su.se.bakover.client.ClientError
import no.nav.su.se.bakover.common.domain.PdfA
import no.nav.su.se.bakover.domain.brev.jsonRequest.PdfInnhold
import no.nav.su.se.bakover.domain.søknad.SøknadPdfInnhold

interface PdfGenerator {
    fun genererPdf(søknadPdfInnhold: SøknadPdfInnhold): Either<ClientError, PdfA>
    fun genererPdf(pdfInnhold: PdfInnhold): Either<KunneIkkeGenererePdf, PdfA>
}

data object KunneIkkeGenererePdf
