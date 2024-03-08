package no.nav.su.se.bakover.datapakker.fritekstAvslag

import java.time.YearMonth

data class AvslagsvedtakUtenFritekst(
    val antall: Int,
    val yearMonth: YearMonth,
)

fun List<AvslagsvedtakUtenFritekst>.toCSV(): String = this.joinToString(separator = "\n") {
    "${it.antall},${it.yearMonth}"
}
