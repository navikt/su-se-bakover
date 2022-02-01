package no.nav.su.se.bakover.client.journalpost

import arrow.core.left
import arrow.core.right
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.journalpost.HentetJournalpost
import no.nav.su.se.bakover.domain.journalpost.JournalpostStatus
import no.nav.su.se.bakover.domain.journalpost.KunneIkkeHenteJournalpost
import no.nav.su.se.bakover.domain.journalpost.Tema
import org.junit.jupiter.api.Test

internal class JournalpostResponseTest {

    @Test
    fun `mapper journalpostResponse til HentetJournalpost`() {
        val jpr = JournalpostResponse(Journalpost("SUP", "FERDIGSTILT", Sak("2021")))
        jpr.toHentetJournalpost(Saksnummer(2021)) shouldBe HentetJournalpost.create(
            tema = Tema.SUP,
            journalstatus = JournalpostStatus.FERDIGSTILT,
            saksnummer = Saksnummer(2021),
        ).right()
    }

    @Test
    fun `får feil dersom journalpost er null, og prøver å mappe til hentetJournalpost`() {
        val jpr = JournalpostResponse(null)
        jpr.toHentetJournalpost(Saksnummer(2022)) shouldBe KunneIkkeHenteJournalpost.FantIkkeJournalpost.left()
    }

    @Test
    fun `får feil dersom tema ikke er SUP`() {
        val jpr = JournalpostResponse(Journalpost("SUPPE", "FERDIGSTILT", Sak("2021")))
        jpr.toHentetJournalpost(Saksnummer(3333)) shouldBe KunneIkkeHenteJournalpost.JournalpostTemaErIkkeSUP.left()
    }

    @Test
    fun `får feil dersom status ikke er ferdigstilt`() {
        val jpr = JournalpostResponse(Journalpost("SUP", "FERDIGSTILTETET", Sak("2021")))
        jpr.toHentetJournalpost(Saksnummer(3333)) shouldBe KunneIkkeHenteJournalpost.JournalpostenErIkkeFerdigstilt.left()
    }

    @Test
    fun `får feil dersom journalposten ikke er knyttet til saken`() {
        val jpr = JournalpostResponse(Journalpost("SUP", "FERDIGSTILT", Sak("2021")))
        jpr.toHentetJournalpost(Saksnummer(3333)) shouldBe KunneIkkeHenteJournalpost.JournalpostIkkeKnyttetTilSak.left()
    }
}
