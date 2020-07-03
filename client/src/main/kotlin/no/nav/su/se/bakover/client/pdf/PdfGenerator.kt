package no.nav.su.se.bakover.client.pdf

import no.nav.su.meldinger.kafka.soknad.NySøknad

interface PdfGenerator {
    fun genererPdf(nySøknad: NySøknad): ByteArray
}
