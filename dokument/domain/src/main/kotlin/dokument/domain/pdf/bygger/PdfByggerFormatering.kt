package dokument.domain.pdf.bygger

import java.time.format.DateTimeFormatter

private val norskDato: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")
private val årMåned: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM")

fun PdfData.kroner(utenDesimaler: Boolean = false): String {
    val streng = tilStreng() ?: return ""
    val deler = streng.split(".")
    val heltall = deler.first().reversed().chunked(3).joinToString("\u00A0").reversed()
    if (utenDesimaler) return heltall
    val desimaler = deler.drop(1).firstOrNull()?.let { (it + "0").substring(0, 2) } ?: "00"
    return "$heltall,$desimaler"
}

fun PdfData.storForbokstav(): String =
    strengEllerNull()?.lowercase()?.replaceFirstChar { it.uppercase() } ?: ""

fun PdfData.isoTilDato(): String {
    val streng = strengEllerNull() ?: return ""
    return try {
        if (streng.split("-").size == 2) {
            norskDato.format(årMåned.parse(streng))
        } else {
            norskDato.format(DateTimeFormatter.ISO_DATE.parse(streng))
        }
    } catch (_: Exception) {
        norskDato.format(DateTimeFormatter.ISO_DATE.parse("$streng-01"))
    }
}

fun List<String>.somOppramsing(): String = when (size) {
    0 -> ""
    1 -> first()
    else -> dropLast(1).joinToString(", ") + " og " + last()
}
