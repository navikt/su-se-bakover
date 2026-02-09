package no.nav.su.se.bakover.service.klage

import arrow.core.Either
import dokument.domain.journalføring.KunneIkkeHenteDokument
import dokument.domain.journalføring.KunneIkkeHenteJournalpost
import no.nav.su.se.bakover.common.journal.JournalpostId
import java.time.LocalDate
import java.util.UUID

interface KlageinstansDokumentService {
    suspend fun hentDokumenterForSak(sakId: UUID): Either<KlageinstansDokumentFeil, List<KlageinstansDokument>>
}

data class KlageinstansDokument(
    val journalpostId: JournalpostId,
    val journalpostTittel: String?,
    val datoOpprettet: LocalDate?,
    val dokumentInfoId: String,
    val dokumentTittel: String?,
    val brevkode: String?,
    val dokumentstatus: String?,
    val variantFormat: String,
    val bytes: ByteArray,
)

sealed interface KlageinstansDokumentFeil {
    data class KunneIkkeHenteJournalpost(val feil: dokument.domain.journalføring.KunneIkkeHenteJournalpost) : KlageinstansDokumentFeil

    data class KunneIkkeHenteDokument(val feil: dokument.domain.journalføring.KunneIkkeHenteDokument) : KlageinstansDokumentFeil
}
