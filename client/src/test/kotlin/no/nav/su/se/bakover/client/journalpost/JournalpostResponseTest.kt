package no.nav.su.se.bakover.client.journalpost

import arrow.core.left
import arrow.core.right
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.domain.journalpost.HentetJournalpost
import org.junit.jupiter.api.Test

internal class JournalpostResponseTest {

    @Test
    fun `mapper journalpostResponse til HentetJournalpost`() {
        val jpr = JournalpostResponse(Journalpost("SUP", "FERDIGSTILT", Sak("1234")))
        jpr.toHentetJournalpost() shouldBe HentetJournalpost.create(
            tema = "SUP",
            journalstatus = "FERDIGSTILT",
            sak = no.nav.su.se.bakover.domain.journalpost.Sak("1234"),
        ).right()
    }

    @Test
    fun `får feil dersom journalpost er null, og prøver å mappe til hentetJournalpost`() {
        val jpr = JournalpostResponse(null)
        jpr.toHentetJournalpost() shouldBe JournalpostFinnesIkke.left()
    }
}
