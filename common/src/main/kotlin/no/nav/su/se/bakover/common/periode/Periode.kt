package no.nav.su.se.bakover.common.periode

import com.fasterxml.jackson.annotation.JsonIgnore
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
        require(getAntallMåneder() <= 12) { "periode må være mindre eller lik 12 måneder" }
    }

    fun getFraOgMed() = fraOgMed
    fun getTilOgMed() = tilOgMed

    @JsonIgnore
    fun getAntallMåneder() = Period.between(fraOgMed, tilOgMed.plusDays(1)).toTotalMonths().toInt()
    fun tilMånedsperioder(): List<Periode> {
        return (0L until getAntallMåneder())
            .map {
                val firstInMonth = fraOgMed.plusMonths(it)
                val lastInMonth = firstInMonth.plusMonths(1).minusDays(1)
                Periode(firstInMonth, lastInMonth)
            }
    }

    infix fun inneholder(other: Periode): Boolean =
        (fraOgMed.isEqual(other.fraOgMed) || fraOgMed.isBefore(other.fraOgMed)) &&
            (tilOgMed.isEqual(other.tilOgMed) || tilOgMed.isAfter(other.tilOgMed))

    infix fun tilstøter(other: Periode): Boolean {
        val sluttStart = Period.between(tilOgMed, other.fraOgMed)
        val startSlutt = Period.between(fraOgMed, other.tilOgMed)
        val plussEnDag = Period.ofDays(1)
        val minusEnDag = Period.ofDays(-1)
        return sluttStart == plussEnDag || sluttStart == minusEnDag || startSlutt == plussEnDag || startSlutt == minusEnDag
    }
}
