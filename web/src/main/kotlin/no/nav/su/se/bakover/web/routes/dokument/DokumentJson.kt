package no.nav.su.se.bakover.web.routes.dokument

import no.nav.su.se.bakover.domain.dokument.Dokument
import java.time.format.DateTimeFormatter

internal fun List<Dokument>.toJson(): List<DokumentJson> {
    return map { it.toJson() }
}

internal fun Dokument.toJson(): DokumentJson {
    return DokumentJson(
        id = id.toString(),
        tittel = tittel,
        opprettet = DateTimeFormatter.ISO_INSTANT.format(opprettet),
        dokument = generertDokument,
        journalført = (this is Dokument.MedMetadata && metadata.journalpostId.isNotNullOrEmpty()),
        brevErBestilt = (this is Dokument.MedMetadata && metadata.brevbestillingId.isNotNullOrEmpty()),
    )
}

internal data class DokumentJson(
    val id: String,
    val tittel: String,
    val opprettet: String,
    val dokument: ByteArray,
    val journalført: Boolean,
    val brevErBestilt: Boolean,
)

private fun String?.isNotNullOrEmpty() = !this.isNullOrEmpty()
