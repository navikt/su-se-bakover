package no.nav.su.se.bakover.client.journalpost

import arrow.core.Either
import arrow.core.right
import dokument.domain.distribuering.Distribueringsadresse
import dokument.domain.journalføring.DokumentInfoMedVarianter
import dokument.domain.journalføring.DokumentInnhold
import dokument.domain.journalføring.DokumentVariant
import dokument.domain.journalføring.ErKontrollNotatMottatt
import dokument.domain.journalføring.ErTilknyttetSak
import dokument.domain.journalføring.Journalpost
import dokument.domain.journalføring.JournalpostMedDokumenter
import dokument.domain.journalføring.JournalpostStatus
import dokument.domain.journalføring.JournalpostTema
import dokument.domain.journalføring.JournalpostType
import dokument.domain.journalføring.KontrollnotatMottattJournalpost
import dokument.domain.journalføring.KunneIkkeHenteDokument
import dokument.domain.journalføring.KunneIkkeHenteJournalpost
import dokument.domain.journalføring.KunneIkkeHenteJournalposter
import dokument.domain.journalføring.KunneIkkeSjekkKontrollnotatMottatt
import dokument.domain.journalføring.KunneIkkeSjekkeTilknytningTilSak
import dokument.domain.journalføring.QueryJournalpostClient
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.journal.JournalpostId
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.periode.DatoIntervall
import java.time.LocalDate

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
        return false.right()
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

    override suspend fun hentJournalpostMedDokumenter(
        journalpostId: JournalpostId,
    ): Either<KunneIkkeHenteJournalpost, JournalpostMedDokumenter> {
        return JournalpostMedDokumenter(
            journalpostId = journalpostId,
            tittel = "Stub journalpost",
            datoOpprettet = LocalDate.of(2020, 1, 1),
            distribueringsadresse = Distribueringsadresse(
                adresselinje1 = "Stubveien 1",
                adresselinje2 = null,
                adresselinje3 = null,
                postnummer = "0001",
                poststed = "Oslo",
            ),
            dokumenter = listOf(
                DokumentInfoMedVarianter(
                    dokumentInfoId = "stub-doc-1",
                    tittel = "Stub dokument",
                    brevkode = "STUB",
                    dokumentstatus = "FERDIGSTILT",
                    varianter = listOf(
                        DokumentVariant(
                            variantFormat = "ARKIV",
                            filtype = "PDF",
                        ),
                    ),
                ),
            ),
        ).right()
    }

    override suspend fun hentDokument(
        journalpostId: JournalpostId,
        dokumentInfoId: String,
        variantFormat: String,
    ): Either<KunneIkkeHenteDokument, DokumentInnhold> {
        return DokumentInnhold(
            bytes = byteArrayOf(),
            contentType = "application/pdf",
            contentDisposition = null,
        ).right()
    }
}
