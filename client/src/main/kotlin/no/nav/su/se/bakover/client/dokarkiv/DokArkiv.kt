package no.nav.su.se.bakover.client.dokarkiv

import no.nav.su.meldinger.kafka.soknad.NySøknad

interface DokArkiv {
    fun opprettJournalpost(nySøknad: NySøknad, pdf: ByteArray): String
}
