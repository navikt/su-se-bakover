package no.nav.su.se.bakover.client.pdf

import arrow.core.Either
import no.nav.su.meldinger.kafka.soknad.SøknadInnhold
import no.nav.su.se.bakover.client.ClientError

interface PdfGenerator {
    fun genererPdf(søknad: SøknadInnhold): Either<ClientError, ByteArray>
}
