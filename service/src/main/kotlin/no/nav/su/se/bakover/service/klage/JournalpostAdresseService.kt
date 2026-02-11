package no.nav.su.se.bakover.service.klage

import arrow.core.Either
import dokument.domain.journalføring.Utsendingsinfo
import no.nav.su.se.bakover.common.journal.JournalpostId
import java.time.LocalDate
import java.util.UUID

interface JournalpostAdresseService {
    suspend fun hentKlageDokumenterAdresseForSak(sakId: UUID): Either<AdresseServiceFeil, List<JournalpostMedDokumentPdfOgAdresse>>
    suspend fun hentAdresseForDokumentId(
        dokumentId: UUID,
        journalpostId: JournalpostId,
    ): Either<AdresseServiceFeil, DokumentUtsendingsinfo>
}

data class DokumentUtsendingsinfo(
    val utsendingsinfo: Utsendingsinfo?,
)

@Suppress("ArrayInDataClass")
data class JournalpostMedDokumentPdfOgAdresse(
    val journalpostId: JournalpostId,
    val journalpostTittel: String?,
    val datoOpprettet: LocalDate?,
    val utsendingsinfo: Utsendingsinfo?,
    val dokumentInfoId: String,
    val dokumentTittel: String?,
    val brevkode: String?,
    val dokumentstatus: String?,
    val variantFormat: String,
    val dokument: ByteArray,
)

sealed interface AdresseServiceFeil {
    data class KunneIkkeHenteJournalpost(val feil: dokument.domain.journalføring.KunneIkkeHenteJournalpost) : AdresseServiceFeil
    data class KunneIkkeHenteDokument(val feil: dokument.domain.journalføring.KunneIkkeHenteDokument) : AdresseServiceFeil
    data object FantIkkeDokument : AdresseServiceFeil
    data object FantIkkeJournalpostForDokument : AdresseServiceFeil
    data object JournalpostIkkeKnyttetTilDokument : AdresseServiceFeil
}
