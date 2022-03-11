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
        val jpr = JournalpostResponse(Journalpost("SUP", "JOURNALFOERT", "I", Sak("2021")))
        jpr.toValidertInnkommendeJournalførtJournalpost(Saksnummer(2021)) shouldBe FerdigstiltJournalpost.create(
            tema = Tema.SUP,
            journalstatus = JournalpostStatus.JOURNALFOERT,
            journalpostType = JournalpostType.INNKOMMENDE_DOKUMENT,
            saksnummer = Saksnummer(2021),
        ).right()
    }

    @Test
    fun `får feil dersom journalpost er null, og prøver å mappe til FerdigstiltJournalpost`() {
        val jpr = JournalpostResponse(null)
        jpr.toValidertInnkommendeJournalførtJournalpost(Saksnummer(2022)) shouldBe KunneIkkeHenteJournalpost.FantIkkeJournalpost.left()
    }

    @Test
    fun `får feil dersom tema er null eller ikke er SUP`() {
        val jpr = JournalpostResponse(Journalpost(null, "JOURNALFOERT", "I", Sak("2021")))
        jpr.toValidertInnkommendeJournalførtJournalpost(Saksnummer(3333)) shouldBe KunneIkkeHenteJournalpost.JournalpostTemaErIkkeSUP.left()

        val jpr2 = JournalpostResponse(Journalpost("UGYLDIG_TEMA", "JOURNALFOERT", "I", Sak("2021")))
        jpr2.toValidertInnkommendeJournalførtJournalpost(Saksnummer(3333)) shouldBe KunneIkkeHenteJournalpost.JournalpostTemaErIkkeSUP.left()
    }

    @Test
    fun `får feil dersom status er null eller ikke er ferdigstilt`() {
        val jpr = JournalpostResponse(Journalpost("SUP", null, "I", Sak("2021")))
        jpr.toValidertInnkommendeJournalførtJournalpost(Saksnummer(3333)) shouldBe KunneIkkeHenteJournalpost.JournalpostenErIkkeFerdigstilt.left()

        val jpr2 = JournalpostResponse(Journalpost("SUP", "UGYLDIG_JOURNALPOSTSTATUS", "I", Sak("2021")))
        jpr2.toValidertInnkommendeJournalførtJournalpost(Saksnummer(3333)) shouldBe KunneIkkeHenteJournalpost.JournalpostenErIkkeFerdigstilt.left()
    }

    @Test
    fun `får feil dersom journalposttype er null eller ikke innkommende`() {
        val jpr = JournalpostResponse(Journalpost("SUP", "JOURNALFOERT", null, Sak("2021")))
        jpr.toValidertInnkommendeJournalførtJournalpost(Saksnummer(2022)) shouldBe KunneIkkeHenteJournalpost.JournalpostenErIkkeEtInnkommendeDokument.left()

        val jpr2 = JournalpostResponse(Journalpost("SUP", "JOURNALFOERT", "UGYLDIG_JOURNALPOSTID", Sak("2021")))
        jpr2.toValidertInnkommendeJournalførtJournalpost(Saksnummer(2022)) shouldBe KunneIkkeHenteJournalpost.JournalpostenErIkkeEtInnkommendeDokument.left()
    }

    @Test
    fun `får feil dersom journalposten ikke er knyttet til saken`() {
        val jpr = JournalpostResponse(Journalpost("SUP", "JOURNALFOERT", "I", null))
        jpr.toValidertInnkommendeJournalførtJournalpost(Saksnummer(3333)) shouldBe KunneIkkeHenteJournalpost.JournalpostIkkeKnyttetTilSak.left()

        val jpr2 = JournalpostResponse(Journalpost("SUP", "JOURNALFOERT", "I", Sak("2021")))
        jpr2.toValidertInnkommendeJournalførtJournalpost(Saksnummer(3333)) shouldBe KunneIkkeHenteJournalpost.JournalpostIkkeKnyttetTilSak.left()
    }
}
