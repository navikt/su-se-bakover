package no.nav.su.se.bakover.client.journalpost

import arrow.core.Either
import arrow.core.right
import no.nav.su.se.bakover.common.journal.JournalpostId
import no.nav.su.se.bakover.common.tid.periode.DatoIntervall
import no.nav.su.se.bakover.domain.journalpost.ErKontrollNotatMottatt
import no.nav.su.se.bakover.domain.journalpost.ErTilknyttetSak
import no.nav.su.se.bakover.domain.journalpost.JournalpostClient
import no.nav.su.se.bakover.domain.journalpost.JournalpostStatus
import no.nav.su.se.bakover.domain.journalpost.JournalpostTema
import no.nav.su.se.bakover.domain.journalpost.JournalpostType
import no.nav.su.se.bakover.domain.journalpost.KontrollnotatMottattJournalpost
import no.nav.su.se.bakover.domain.journalpost.KunneIkkeSjekkKontrollnotatMottatt
import no.nav.su.se.bakover.domain.journalpost.KunneIkkeSjekkeTilknytningTilSak
import no.nav.su.se.bakover.domain.sak.Saksnummer

object JournalpostClientStub : JournalpostClient {
    override suspend fun erTilknyttetSak(
        journalpostId: JournalpostId,
        saksnummer: Saksnummer,
    ): Either<KunneIkkeSjekkeTilknytningTilSak, ErTilknyttetSak> {
        return ErTilknyttetSak.Ja.right()
    }

    override fun kontrollnotatMotatt(saksnummer: Saksnummer, periode: DatoIntervall): Either<KunneIkkeSjekkKontrollnotatMottatt, ErKontrollNotatMottatt> {
        return ErKontrollNotatMottatt.Ja(
            KontrollnotatMottattJournalpost(
                tema = JournalpostTema.SUP,
                journalstatus = JournalpostStatus.JOURNALFOERT,
                journalposttype = JournalpostType.INNKOMMENDE_DOKUMENT,
                saksnummer = saksnummer,
                tittel = "NAV SU Kontrollnotat",
                datoOpprettet = periode.fraOgMed,
                journalpostId = JournalpostId("453812134"),
            ),
        ).right()
    }
}
