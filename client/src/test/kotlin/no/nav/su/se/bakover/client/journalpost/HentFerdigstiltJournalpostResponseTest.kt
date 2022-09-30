package no.nav.su.se.bakover.client.journalpost

import arrow.core.left
import arrow.core.right
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.journalpost.FerdigstiltJournalpost
import no.nav.su.se.bakover.domain.journalpost.JournalpostStatus
import no.nav.su.se.bakover.domain.journalpost.JournalpostTema
import no.nav.su.se.bakover.domain.journalpost.JournalpostType
import org.junit.jupiter.api.Test

internal class HentFerdigstiltJournalpostResponseTest {

    @Test
    fun `mapper journalpostResponse til FerdigstiltJournalpost`() {
        val jpr = HentJournalpostResponse(Journalpost("SUP", "JOURNALFOERT", "I", Sak("2021")))
        jpr.journalpost.toFerdigstiltJournalpost(Saksnummer(2021)) shouldBe FerdigstiltJournalpost(
            tema = JournalpostTema.SUP,
            journalstatus = JournalpostStatus.JOURNALFOERT,
            journalposttype = JournalpostType.INNKOMMENDE_DOKUMENT,
            saksnummer = Saksnummer(2021),
        ).right()
    }

    @Test
    fun `får feil dersom journalpost er null, og prøver å mappe til FerdigstiltJournalpost`() {
        val jpr = HentJournalpostResponse(null)
        jpr.journalpost.toFerdigstiltJournalpost(Saksnummer(2022)) shouldBe JournalpostErIkkeFerdigstilt.FantIkkeJournalpost.left()
    }

    @Test
    fun `får feil dersom tema er null eller ikke er SUP`() {
        val jpr = HentJournalpostResponse(Journalpost(null, "JOURNALFOERT", "I", Sak("2021")))
        jpr.journalpost.toFerdigstiltJournalpost(Saksnummer(3333)) shouldBe JournalpostErIkkeFerdigstilt.JournalpostTemaErIkkeSUP.left()

        val jpr2 = HentJournalpostResponse(Journalpost("UGYLDIG_TEMA", "JOURNALFOERT", "I", Sak("2021")))
        jpr2.journalpost.toFerdigstiltJournalpost(Saksnummer(3333)) shouldBe JournalpostErIkkeFerdigstilt.JournalpostTemaErIkkeSUP.left()
    }

    @Test
    fun `får feil dersom status er null eller ikke er ferdigstilt`() {
        val jpr = HentJournalpostResponse(Journalpost("SUP", null, "I", Sak("2021")))
        jpr.journalpost.toFerdigstiltJournalpost(Saksnummer(3333)) shouldBe JournalpostErIkkeFerdigstilt.JournalpostenErIkkeFerdigstilt.left()

        val jpr2 = HentJournalpostResponse(Journalpost("SUP", "UGYLDIG_JOURNALPOSTSTATUS", "I", Sak("2021")))
        jpr2.journalpost.toFerdigstiltJournalpost(Saksnummer(3333)) shouldBe JournalpostErIkkeFerdigstilt.JournalpostenErIkkeFerdigstilt.left()
    }

    @Test
    fun `får feil dersom journalposttype er null eller ikke innkommende`() {
        val jpr = HentJournalpostResponse(Journalpost("SUP", "JOURNALFOERT", null, Sak("2021")))
        jpr.journalpost.toFerdigstiltJournalpost(Saksnummer(2022)) shouldBe JournalpostErIkkeFerdigstilt.JournalpostenErIkkeEtInnkommendeDokument.left()

        val jpr2 = HentJournalpostResponse(Journalpost("SUP", "JOURNALFOERT", "UGYLDIG_JOURNALPOSTID", Sak("2021")))
        jpr2.journalpost.toFerdigstiltJournalpost(Saksnummer(2022)) shouldBe JournalpostErIkkeFerdigstilt.JournalpostenErIkkeEtInnkommendeDokument.left()
    }

    @Test
    fun `får feil dersom journalposten ikke er knyttet til saken`() {
        val jpr = HentJournalpostResponse(Journalpost("SUP", "JOURNALFOERT", "I", null))
        jpr.journalpost.toFerdigstiltJournalpost(Saksnummer(3333)) shouldBe JournalpostErIkkeFerdigstilt.JournalpostIkkeKnyttetTilSak.left()

        val jpr2 = HentJournalpostResponse(Journalpost("SUP", "JOURNALFOERT", "I", Sak("2021")))
        jpr2.journalpost.toFerdigstiltJournalpost(Saksnummer(3333)) shouldBe JournalpostErIkkeFerdigstilt.JournalpostIkkeKnyttetTilSak.left()
    }
}
