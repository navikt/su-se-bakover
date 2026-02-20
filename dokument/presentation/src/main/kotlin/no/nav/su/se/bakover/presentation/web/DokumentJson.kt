package no.nav.su.se.bakover.presentation.web

import dokument.domain.Dokument
import dokument.domain.Dokument.MedMetadata
import no.nav.su.se.bakover.common.serialize
import java.time.format.DateTimeFormatter

fun List<Dokument>.toJson(): String {
    return serialize(
        this.map {
            it.toSummaryJson()
        },
    )
}

fun Dokument.toJson(): String {
    return DokumentJson(
        id = id.toString(),
        tittel = tittel,
        opprettet = DateTimeFormatter.ISO_INSTANT.format(opprettet),
        dokument = generertDokument.getContent(),
        journalpostId = if (this is MedMetadata) metadata.journalpostId else null,
        journalført = erJournalført(),
        brevErBestilt = erBrevBestilt(),
    ).let {
        serialize(it)
    }
}

private fun Dokument.toSummaryJson(): DokumentSummaryJson {
    return DokumentSummaryJson(
        id = id.toString(),
        tittel = tittel,
        opprettet = DateTimeFormatter.ISO_INSTANT.format(opprettet),
        type = dokumentType(),
        pdfUrl = "/api/dokumenter/$id/pdf",
        journalført = erJournalført(),
        journalpostId = if (this is MedMetadata) metadata.journalpostId else null,
        brevErBestilt = erBrevBestilt(),
        brevbestillingId = if (this is MedMetadata) metadata.brevbestillingId else null,
    )
}

private fun Dokument.dokumentType(): String {
    return when (this) {
        is Dokument.MedMetadata.Informasjon.Annet,
        is Dokument.UtenMetadata.Informasjon.Annet,
        -> "ANNET"

        is Dokument.MedMetadata.Informasjon.Viktig,
        is Dokument.UtenMetadata.Informasjon.Viktig,
        -> "VIKTIG"

        is Dokument.MedMetadata.Vedtak,
        is Dokument.UtenMetadata.Vedtak,
        -> "VEDTAK"
    }
}

@Suppress("ArrayInDataClass")
private data class DokumentJson(
    val id: String,
    val tittel: String,
    val opprettet: String,
    val dokument: ByteArray,
    val journalført: Boolean,
    val journalpostId: String? = null,
    val brevErBestilt: Boolean,
)

private data class DokumentSummaryJson(
    val id: String,
    val tittel: String,
    val opprettet: String,
    val type: String,
    val pdfUrl: String,
    val journalført: Boolean,
    val journalpostId: String?,
    val brevErBestilt: Boolean,
    val brevbestillingId: String?,
)
