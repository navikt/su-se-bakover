package no.nav.su.se.bakover.client.journalpost

import arrow.core.Either
import arrow.core.right
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.journalpost.ErKontrollNotatMottatt
import no.nav.su.se.bakover.domain.journalpost.FerdigstiltJournalpost
import no.nav.su.se.bakover.domain.journalpost.JournalpostClient
import no.nav.su.se.bakover.domain.journalpost.JournalpostStatus
import no.nav.su.se.bakover.domain.journalpost.JournalpostTema
import no.nav.su.se.bakover.domain.journalpost.JournalpostType
import no.nav.su.se.bakover.domain.journalpost.KontrollnotatMottattJournalpost
import no.nav.su.se.bakover.domain.journalpost.KunneIkkeHenteJournalpost
import no.nav.su.se.bakover.domain.journalpost.KunneIkkeSjekkKontrollnotatMottatt

object JournalpostClientStub : JournalpostClient {
    override fun hentFerdigstiltJournalpost(
        saksnummer: Saksnummer,
        journalpostId: JournalpostId,
    ): Either<KunneIkkeHenteJournalpost, FerdigstiltJournalpost> {
        return FerdigstiltJournalpost(
            tema = JournalpostTema.SUP,
            journalstatus = JournalpostStatus.JOURNALFOERT,
            journalposttype = JournalpostType.INNKOMMENDE_DOKUMENT,
            saksnummer = saksnummer,
        ).right()
    }

    override fun kontrollnotatMotatt(saksnummer: Saksnummer, periode: Periode): Either<KunneIkkeSjekkKontrollnotatMottatt, ErKontrollNotatMottatt.Ja> {
        return ErKontrollNotatMottatt.Ja(
            KontrollnotatMottattJournalpost(
                tema = JournalpostTema.SUP,
                journalstatus = JournalpostStatus.JOURNALFOERT,
                journalposttype = JournalpostType.INNKOMMENDE_DOKUMENT,
                saksnummer = saksnummer,
                tittel = "NAV SU Kontrollnotat",
                datoOpprettet = periode.fraOgMed,
                journalpostId = JournalpostId("453812134")
            )
        ).right()
    }
}
