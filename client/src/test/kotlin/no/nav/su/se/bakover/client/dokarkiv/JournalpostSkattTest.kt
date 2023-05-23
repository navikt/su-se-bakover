package no.nav.su.se.bakover.client.dokarkiv

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.client.dokarkiv.JournalpostSkatt.Companion.lagJournalpost
import no.nav.su.se.bakover.test.person
import no.nav.su.se.bakover.test.sakinfo
import no.nav.su.se.bakover.test.skatt.nySkattedokumentGenerert
import org.junit.jupiter.api.Test

class JournalpostSkattTest {

    @Test
    fun `lager journalpost`() {
        val person = person()
        val journalpost = nySkattedokumentGenerert().lagJournalpost(person, sakinfo)
        journalpost.journalpostType shouldBe JournalPostType.NOTAT
        journalpost.journalfoerendeEnhet shouldBe JournalførendeEnhet.AUTOMATISK
        journalpost.bruker shouldBe Bruker(person.ident.fnr.toString())
        journalpost.kanal shouldBe null
        journalpost.avsenderMottaker shouldBe null
    }
}
