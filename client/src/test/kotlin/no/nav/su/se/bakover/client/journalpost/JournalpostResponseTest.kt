package no.nav.su.se.bakover.client.journalpost

import arrow.core.left
import arrow.core.right
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.journalpost.FerdigstiltJournalpost
import no.nav.su.se.bakover.domain.journalpost.JournalpostStatus
import no.nav.su.se.bakover.domain.journalpost.JournalpostType
import no.nav.su.se.bakover.domain.journalpost.KunneIkkeHenteJournalpost
import no.nav.su.se.bakover.domain.journalpost.Tema
import org.junit.jupiter.api.Test

internal class JournalpostResponseTest {

    @Test
    fun `mapper journalpostResponse til FerdigstiltJournalpost`() {
        val jpr = JournalpostResponse(Journalpost("SUP", "FERDIGSTILT", "U", Sak("2021")))
        jpr.toValidertJournalpost(Saksnummer(2021)) shouldBe FerdigstiltJournalpost.create(
            tema = Tema.SUP,
            journalstatus = JournalpostStatus.FERDIGSTILT,
            journalpostType = JournalpostType.UTGÅENDE_DOKUMENT,
            saksnummer = Saksnummer(2021),
        ).right()
    }

    @Test
    fun `får feil dersom journalpost er null, og prøver å mappe til FerdigstiltJournalpost`() {
        val jpr = JournalpostResponse(null)
        jpr.toValidertJournalpost(Saksnummer(2022)) shouldBe KunneIkkeHenteJournalpost.FantIkkeJournalpost.left()
    }

    @Test
    fun `får feil dersom tema er null eller ikke er SUP`() {
        val jpr = JournalpostResponse(Journalpost(null, "FERDIGSTILT", "U", Sak("2021")))
        jpr.toValidertJournalpost(Saksnummer(3333)) shouldBe KunneIkkeHenteJournalpost.JournalpostTemaErIkkeSUP.left()

        val jpr2 = JournalpostResponse(Journalpost("SUPPE", "FERDIGSTILT", "U", Sak("2021")))
        jpr2.toValidertJournalpost(Saksnummer(3333)) shouldBe KunneIkkeHenteJournalpost.JournalpostTemaErIkkeSUP.left()
    }

    @Test
    fun `får feil dersom status er null eller ikke er ferdigstilt`() {
        val jpr = JournalpostResponse(Journalpost("SUP", null, "U", Sak("2021")))
        jpr.toValidertJournalpost(Saksnummer(3333)) shouldBe KunneIkkeHenteJournalpost.JournalpostenErIkkeFerdigstilt.left()

        val jpr2 = JournalpostResponse(Journalpost("SUP", "FERDIGSTILTETET", "U", Sak("2021")))
        jpr2.toValidertJournalpost(Saksnummer(3333)) shouldBe KunneIkkeHenteJournalpost.JournalpostenErIkkeFerdigstilt.left()
    }

    @Test
    fun `får feil dersom journalposttype er null eller ikke utgående`() {
        val jpr = JournalpostResponse(Journalpost("SUP", "FERDIGSTILT", null, Sak("2021")))
        jpr.toValidertJournalpost(Saksnummer(2022)) shouldBe KunneIkkeHenteJournalpost.JournalpostenErIkkeEtUtgåendeDokument.left()

        val jpr2 = JournalpostResponse(Journalpost("SUP", "FERDIGSTILT", ":)", Sak("2021")))
        jpr2.toValidertJournalpost(Saksnummer(2022)) shouldBe KunneIkkeHenteJournalpost.JournalpostenErIkkeEtUtgåendeDokument.left()
    }

    @Test
    fun `får feil dersom journalposten ikke er knyttet til saken`() {
        val jpr = JournalpostResponse(Journalpost("SUP", "FERDIGSTILT", "U", null))
        jpr.toValidertJournalpost(Saksnummer(3333)) shouldBe KunneIkkeHenteJournalpost.JournalpostIkkeKnyttetTilSak.left()

        val jpr2 = JournalpostResponse(Journalpost("SUP", "FERDIGSTILT", "U", Sak("2021")))
        jpr2.toValidertJournalpost(Saksnummer(3333)) shouldBe KunneIkkeHenteJournalpost.JournalpostIkkeKnyttetTilSak.left()
    }
}
