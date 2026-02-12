package no.nav.su.se.bakover.presentation.web

import dokument.domain.Dokument
import dokument.domain.Dokument.MedMetadata
import no.nav.su.se.bakover.common.serialize
import java.time.format.DateTimeFormatter

fun List<Dokument>.toJson(): String {
    return joinToString(separator = ",\n", prefix = "[", postfix = "]") { it.toJson() }
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
