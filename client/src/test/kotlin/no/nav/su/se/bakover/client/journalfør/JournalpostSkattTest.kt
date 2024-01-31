package no.nav.su.se.bakover.client.journalfør

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.test.sakinfo
import no.nav.su.se.bakover.test.skatt.nySkattedokumentGenerert
import org.junit.jupiter.api.Test
import vilkår.skatt.domain.journalpost.JournalførSkattedokumentPåSakCommand.Companion.lagJournalpost

class JournalpostSkattTest {

    @Test
    fun `lager journalpost`() {
        val dokument = nySkattedokumentGenerert()
        val journalpost = dokument.lagJournalpost(sakinfo)
        journalpost.saksnummer shouldBe sakinfo.saksnummer
        journalpost.fnr shouldBe sakinfo.fnr
        journalpost.sakstype shouldBe sakinfo.type
        journalpost.dokument shouldBe dokument
    }
}
