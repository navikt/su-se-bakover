package no.nav.su.se.bakover.client.stubs.pdf

import no.nav.su.meldinger.kafka.soknad.NySøknad
import no.nav.su.se.bakover.client.pdf.PdfGenerator

object PdfGeneratorStub : PdfGenerator {
    override fun genererPdf(nySøknad: NySøknad): ByteArray {
        return nySøknad.søknad.toByteArray()
    }
}
