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
    )
}

internal data class DokumentJson(
    val id: String,
    val tittel: String,
    val opprettet: String,
    val dokument: ByteArray,
)
