package no.nav.su.se.bakover.client.stubs.pdf

import arrow.core.Either
import arrow.core.right
import no.nav.su.meldinger.kafka.soknad.SøknadInnhold
import no.nav.su.se.bakover.client.ClientError
import no.nav.su.se.bakover.client.pdf.PdfGenerator

object PdfGeneratorStub : PdfGenerator {
    override fun genererPdf(søknad: SøknadInnhold): Either<ClientError, ByteArray> {
        return søknad.toJson().toByteArray().right()
    }
}
