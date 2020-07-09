package no.nav.su.se.bakover.client.stubs.pdf

import arrow.core.Either
import arrow.core.right
import no.nav.su.se.bakover.client.ClientError
import no.nav.su.se.bakover.client.pdf.PdfGenerator
import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.domain.SøknadInnhold

object PdfGeneratorStub : PdfGenerator {
    override fun genererPdf(søknad: SøknadInnhold): Either<ClientError, ByteArray> {
        return objectMapper.writeValueAsString(søknad).toByteArray().right()
    }
}
