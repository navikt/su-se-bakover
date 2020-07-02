package no.nav.su.se.bakover.client.stubs

import no.nav.su.meldinger.kafka.soknad.NySøknad
import no.nav.su.se.bakover.client.pdf.PdfGenerator

object PdfGeneratorStub : PdfGenerator {
    override fun genererPdf(nySøknad: NySøknad): ByteArray {
        return "123".toByteArray()
    }
}
