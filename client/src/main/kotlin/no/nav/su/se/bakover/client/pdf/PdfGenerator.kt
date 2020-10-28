package no.nav.su.se.bakover.client.pdf

import arrow.core.Either
import no.nav.su.se.bakover.client.ClientError
import no.nav.su.se.bakover.domain.SøknadInnhold
import no.nav.su.se.bakover.domain.brev.BrevInnhold

interface PdfGenerator {
    fun genererPdf(søknad: SøknadInnhold): Either<ClientError, ByteArray>
    fun genererPdf(brevInnhold: BrevInnhold): Either<KunneIkkeGenererePdf, ByteArray>
}

object KunneIkkeGenererePdf
