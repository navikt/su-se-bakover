package no.nav.su.se.bakover.client.pdf

import arrow.core.Either
import no.nav.su.se.bakover.client.ClientError
import no.nav.su.se.bakover.domain.brev.BrevInnhold
import no.nav.su.se.bakover.domain.søknad.SøknadPdfInnhold

interface PdfGenerator {
    fun genererPdf(søknadPdfInnhold: SøknadPdfInnhold): Either<ClientError, ByteArray>
    fun genererPdf(brevInnhold: BrevInnhold): Either<KunneIkkeGenererePdf, ByteArray>
}

object KunneIkkeGenererePdf
