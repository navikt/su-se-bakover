package no.nav.su.se.bakover.client.journalpost

import arrow.core.Either
import arrow.core.right
import dokument.domain.journalføring.ErKontrollNotatMottatt
import dokument.domain.journalføring.ErTilknyttetSak
import dokument.domain.journalføring.Journalpost
import dokument.domain.journalføring.JournalpostStatus
import dokument.domain.journalføring.JournalpostTema
import dokument.domain.journalføring.JournalpostType
import dokument.domain.journalføring.KontrollnotatMottattJournalpost
import dokument.domain.journalføring.KunneIkkeHenteJournalposter
import dokument.domain.journalføring.KunneIkkeSjekkKontrollnotatMottatt
import dokument.domain.journalføring.KunneIkkeSjekkeTilknytningTilSak
import dokument.domain.journalføring.QueryJournalpostClient
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.journal.JournalpostId
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.periode.DatoIntervall

data object QueryJournalpostClientStub : QueryJournalpostClient {
    override suspend fun erTilknyttetSak(
        journalpostId: JournalpostId,
        saksnummer: Saksnummer,
    ): Either<KunneIkkeSjekkeTilknytningTilSak, ErTilknyttetSak> {
        return ErTilknyttetSak.Ja.right()
    }

    override fun hentJournalposterFor(
        saksnummer: Saksnummer,
        limit: Int,
    ): Either<KunneIkkeHenteJournalposter, List<Journalpost>> =
        listOf(
            Journalpost(JournalpostId("453812134"), "Innsendt klage"),
            Journalpost(JournalpostId("234252334"), "Innsendt klage V2"),
        ).right()

    override fun finnesFagsak(fnr: Fnr, fagsystemId: String, limit: Int): Either<KunneIkkeHenteJournalposter, Boolean> {
        return true.right()
    }

    override fun kontrollnotatMotatt(
        saksnummer: Saksnummer,
        periode: DatoIntervall,
    ): Either<KunneIkkeSjekkKontrollnotatMottatt, ErKontrollNotatMottatt> {
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
