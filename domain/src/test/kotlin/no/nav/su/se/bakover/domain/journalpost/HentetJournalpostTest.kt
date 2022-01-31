package no.nav.su.se.bakover.domain.journalpost

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

internal class HentetJournalpostTest {

    @Test
    fun `journalpost valideres riktig`() {
        val hjp = HentetJournalpost.create("SUP")
        hjp.validerJournalpost() shouldBe true
    }

    @Test
    fun `journalpost valideres feil`() {
        val hjp = HentetJournalpost.create("SUP(PE)")
        hjp.validerJournalpost() shouldBe false
    }
}
