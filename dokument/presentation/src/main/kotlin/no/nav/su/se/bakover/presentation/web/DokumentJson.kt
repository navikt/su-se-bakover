package no.nav.su.se.bakover.presentation.web

import dokument.domain.Dokument
import no.nav.su.se.bakover.common.serialize
import java.time.format.DateTimeFormatter

fun List<Dokument>.toJson(): String {
    return joinToString(separator = ",\n", prefix = "[", postfix = "]") { it.toJson() }
}

fun Dokument.toJson(): String {
    return DokumentJson(
        id = id.toString(),
        tittel = tittel,
        // TODO jah: Tidspunkt bør formateres mer enhetlig mot frontend.
        opprettet = DateTimeFormatter.ISO_INSTANT.format(opprettet),
        dokument = generertDokument.getContent(),
        journalført = erJournalført(),
        brevErBestilt = erBrevBestilt(),
    ).let {
        serialize(it)
    }
}

private data class DokumentJson(
    val id: String,
    val tittel: String,
    val opprettet: String,
    // TODO jah: Her bør vi heller konvertere til base64 selv; istedenfor at Jackson gjør det automagisk for oss.
    val dokument: ByteArray,
    val journalført: Boolean,
    val brevErBestilt: Boolean,
)
