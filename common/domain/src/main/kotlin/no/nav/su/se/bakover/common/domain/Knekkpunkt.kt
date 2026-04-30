package no.nav.su.se.bakover.common.domain

import no.nav.su.se.bakover.common.tid.periode.Måned
import java.time.LocalDate
import java.time.YearMonth

/**
 * Ikrafttredelsesdatoen til en gitt lov/sats.
 * Brukes for å finne ut hvilke satser som gjaldt på en gitt dato.
 */
@JvmInline
value class Knekkpunkt(
    private val value: LocalDate,
) {
    fun måned(): Måned = Måned.fra(YearMonth.of(value.year, value.month))

    companion object {
        operator fun LocalDate.compareTo(knekkpunkt: Knekkpunkt): Int {
            return this.compareTo(knekkpunkt.value)
        }
    }
}
