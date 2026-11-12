package dokument.domain.generer.bygger

import java.time.format.DateTimeFormatter

private val norskDato: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")
private val årMåned: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM")

fun GenerellData.kroner(utenDesimaler: Boolean = false): String =
    tilStreng()?.let { kroner(it, utenDesimaler) } ?: ""

fun Int.kroner(utenDesimaler: Boolean = false): String = kroner(toString(), utenDesimaler)

fun Double.kroner(utenDesimaler: Boolean = false): String = kroner(toBigDecimal().toPlainString(), utenDesimaler)

private fun kroner(streng: String, utenDesimaler: Boolean): String {
    val deler = streng.split(".")
    val heltall = deler.first().reversed().chunked(3).joinToString("\u00A0").reversed()
    if (utenDesimaler) return heltall
    val desimaler = deler.drop(1).firstOrNull()?.let { (it + "0").substring(0, 2) } ?: "00"
    return "$heltall,$desimaler"
}

fun GenerellData.storForbokstav(): String =
    strengEllerNull()?.storForbokstav() ?: ""

fun String.storForbokstav(): String =
    lowercase().replaceFirstChar { it.uppercase() }

fun GenerellData.isoTilDato(): String {
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
