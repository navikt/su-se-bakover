package no.nav.su.se.bakover.web.routes.dokument

import no.nav.su.se.bakover.domain.dokument.Dokument
import java.time.format.DateTimeFormatter

internal fun List<Dokument>.toJson(): List<DokumentJson> {
    return map { it.toJson() }
}

internal fun Dokument.toJson(): DokumentJson {
    return DokumentJson(
        tittel = tittel,
        opprettet = DateTimeFormatter.ISO_INSTANT.format(opprettet),
        dokument = generertDokument,
    )
}

internal data class DokumentJson(
    val tittel: String,
    val opprettet: String,
    val dokument: ByteArray,
)
