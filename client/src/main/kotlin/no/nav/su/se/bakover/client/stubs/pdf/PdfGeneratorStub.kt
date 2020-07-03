package no.nav.su.se.bakover.client.stubs.pdf

import arrow.core.Either
import arrow.core.right
import no.nav.su.meldinger.kafka.soknad.NySøknad
import no.nav.su.se.bakover.client.ClientError
import no.nav.su.se.bakover.client.pdf.PdfGenerator

object PdfGeneratorStub : PdfGenerator {
    override fun genererPdf(nySøknad: NySøknad): Either<ClientError, ByteArray> {
        return nySøknad.søknad.toByteArray().right()
    }
}
