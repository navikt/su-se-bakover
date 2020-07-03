package no.nav.su.se.bakover.client.pdf

import arrow.core.Either
import no.nav.su.meldinger.kafka.soknad.NySøknad
import no.nav.su.se.bakover.client.ClientError

interface PdfGenerator {
    fun genererPdf(nySøknad: NySøknad): Either<ClientError, ByteArray>
}
