package no.nav.su.se.bakover.domain.journalpost

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.domain.Saksnummer
import org.junit.jupiter.api.Test

internal class HentetJournalpostTest {

    @Test
    fun `journalpost valideres riktig`() {
        val hjp = HentetJournalpost.create("SUP", "FERDIGSTILT", Sak("1234"))
        hjp.validerJournalpost(Saksnummer(2021)) shouldBe true
    }

    @Test
    fun `journalpost valideres feil`() {
        val hjp = HentetJournalpost.create("SUP(PE)", "JOURNALFOERT", Sak("1234"))
        hjp.validerJournalpost(Saksnummer(2021)) shouldBe false
    }
}
