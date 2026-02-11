package dokument.domain.journalføring

import arrow.core.Either
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.journal.JournalpostId
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.periode.DatoIntervall
import java.time.LocalDate

interface QueryJournalpostClient {
    /**
     * Sjekker om aktuell [journalpostId] er knyttet til [saksnummer]
     */
    suspend fun erTilknyttetSak(
        journalpostId: JournalpostId,
        saksnummer: Saksnummer,
    ): Either<KunneIkkeSjekkeTilknytningTilSak, ErTilknyttetSak>

    fun hentJournalposterFor(
        saksnummer: Saksnummer,
        limit: Int = 50,
    ): Either<KunneIkkeHenteJournalposter, List<Journalpost>>

    fun finnesFagsak(fnr: Fnr, fagsystemId: String, limit: Int = 100): Either<KunneIkkeHenteJournalposter, Boolean>

    /**
     * Skreddersydd for å svare på om det er mottatt et kontrollnotat for [saksnummer] i løpet av gitt [periode].
     */
    fun kontrollnotatMotatt(
        saksnummer: Saksnummer,
        periode: DatoIntervall,
    ): Either<KunneIkkeSjekkKontrollnotatMottatt, ErKontrollNotatMottatt>

    /**
     * Henter journalpost med dokumentmetadata (inkl. tilgjengelige varianter).
     */
    suspend fun hentJournalpostMedDokumenter(
        journalpostId: JournalpostId,
    ): Either<KunneIkkeHenteJournalpost, JournalpostMedDokumenter>

    /**
     * Henter fysisk dokument (rå bytes) fra SAF.
     */
    suspend fun hentDokumentForJournalpost(
        journalpostId: JournalpostId,
        dokumentInfoId: String,
        variantFormat: String,
    ): Either<KunneIkkeHenteDokument, DokumentInnhold>
}

data class KunneIkkeSjekkKontrollnotatMottatt(val feil: Any)

sealed interface ErKontrollNotatMottatt {
    data object Nei : ErKontrollNotatMottatt
    data class Ja(val kontrollnotat: KontrollnotatMottattJournalpost) : ErKontrollNotatMottatt
}

sealed interface ErTilknyttetSak {
    data object Ja : ErTilknyttetSak
    data object Nei : ErTilknyttetSak
}

sealed interface KunneIkkeSjekkeTilknytningTilSak {
    data object Ukjent : KunneIkkeSjekkeTilknytningTilSak
    data object FantIkkeJournalpost : KunneIkkeSjekkeTilknytningTilSak
    data object IkkeTilgang : KunneIkkeSjekkeTilknytningTilSak
    data object TekniskFeil : KunneIkkeSjekkeTilknytningTilSak
    data object UgyldigInput : KunneIkkeSjekkeTilknytningTilSak
    data object JournalpostIkkeKnyttetTilSak : KunneIkkeSjekkeTilknytningTilSak
}

sealed interface KunneIkkeHenteJournalposter {
    data object ClientError : KunneIkkeHenteJournalposter
}

data class JournalpostMedDokumenter(
    val journalpostId: JournalpostId,
    val tittel: String?,
    val datoOpprettet: LocalDate?,
    val utsendingsinfo: Utsendingsinfo?,
    val dokumenter: List<DokumentInfoMedVarianter>,
)

data class Utsendingsinfo(
    val fysiskpostSendt: String?,
    val digitalpostSendt: String?,
)

data class DokumentInfoMedVarianter(
    val dokumentInfoId: String,
    val tittel: String?,
    val brevkode: String?,
    val dokumentstatus: String?,
    val varianter: List<DokumentVariant>,
)

data class DokumentVariant(
    val variantFormat: String,
    val filtype: String?,
)

@Suppress("ArrayInDataClass")
data class DokumentInnhold(
    val bytes: ByteArray,
    val contentType: String?,
    val contentDisposition: String?,
)

sealed interface KunneIkkeHenteJournalpost {
    data object FantIkkeJournalpost : KunneIkkeHenteJournalpost
    data object IkkeTilgang : KunneIkkeHenteJournalpost
    data object UgyldigInput : KunneIkkeHenteJournalpost
    data object TekniskFeil : KunneIkkeHenteJournalpost
    data object Ukjent : KunneIkkeHenteJournalpost
}

sealed interface KunneIkkeHenteDokument {
    data object FantIkkeDokument : KunneIkkeHenteDokument
    data object IkkeTilgang : KunneIkkeHenteDokument
    data object IkkeAutorisert : KunneIkkeHenteDokument
    data object UgyldigInput : KunneIkkeHenteDokument
    data class TekniskFeil(val msg: String) : KunneIkkeHenteDokument
    data class Ukjent(val msg: String) : KunneIkkeHenteDokument
}
