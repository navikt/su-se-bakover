package no.nav.su.se.bakover.common.periode

import java.time.LocalDate
import java.time.Period

data class Periode(
    private val fraOgMed: LocalDate,
    private val tilOgMed: LocalDate
) {
    init {
        require(fraOgMed.dayOfMonth == 1) { "Perioder kan kun starte på første dag i måneden" }
        require(tilOgMed.dayOfMonth == tilOgMed.lengthOfMonth()) { "Perioder kan kun avsluttes siste dag i måneden" }
        require(fraOgMed.isBefore(tilOgMed)) { "fraOgMed må være tidligere enn tilOgMed" }
    }

    fun fraOgMed() = fraOgMed
    fun tilOgMed() = tilOgMed
    fun antallMåneder() = Period.between(fraOgMed, tilOgMed.plusDays(1)).toTotalMonths().toInt()
    fun tilMånedsperioder(): List<Periode> {
        return (0L until antallMåneder())
            .map {
                val firstInMonth = fraOgMed.plusMonths(it)
                val lastInMonth = firstInMonth.plusMonths(1).minusDays(1)
                Periode(firstInMonth, lastInMonth)
            }
    }
}
