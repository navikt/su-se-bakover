package no.nav.su.se.bakover.common.periode

import com.fasterxml.jackson.annotation.JsonCreator
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

data class Månedsperiode(
    private val årOgMåned: YearMonth,
) : Periode(årOgMåned) {
    /** Brukes for å deserialisere fra json */
    @JsonCreator
    constructor(fraOgMed: LocalDate, tilOgMed: LocalDate) : this(YearMonth.of(fraOgMed.year, fraOgMed.month)) {
        require(fraOgMed.year == tilOgMed.year)
        require(fraOgMed.month == tilOgMed.month)
        validateOrThrow(fraOgMed, tilOgMed)
    }

    /**
     * Returns a range from this value up to but excluding the specified to value.
     * If the to value is less than or equal to this value, then the returned range is empty.
     */
    fun until(endExclusive: Månedsperiode): List<Månedsperiode> {
        return (0 until this.årOgMåned.until(endExclusive.årOgMåned, ChronoUnit.MONTHS)).map {
            this.plusMonths(it)
        }
    }

    fun plusMonths(monthsToAdd: Long): Månedsperiode {
        return Månedsperiode(årOgMåned.plusMonths(monthsToAdd))
    }

    companion object {
        fun now(clock: Clock): Månedsperiode {
            return Månedsperiode(YearMonth.now(clock.zone))
        }
    }

    override fun equals(other: Any?) = super.equals(other)
    override fun hashCode() = super.hashCode()
}

/**
 * @throws IllegalArgumentException dersom denne perioden er lengre enn 1 måned.
 */
fun Periode.toMånedsperiode(): Månedsperiode {
    require(this.getAntallMåneder() == 1)
    return Månedsperiode(YearMonth.of(this.fraOgMed.year, this.fraOgMed.month))
}

/**
 * Mappet med månedsperioder trenger ikke være sortert eller sammenhengende og kan ha duplikater.
 * @throws NoSuchElementException dersom mappet er tomt.
 */
fun <T> Map<Månedsperiode, T>.periode() = this.keys.toList().minAndMaxOf()
