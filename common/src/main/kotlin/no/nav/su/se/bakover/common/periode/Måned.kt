package no.nav.su.se.bakover.common.periode

import com.fasterxml.jackson.annotation.JsonCreator
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

data class Måned(
    // Vi ønsker ikke ha denne i json enda, men holder oss til Periode sin fraOgMed og tilOgMed
    private val årOgMåned: YearMonth,
) : Periode(årOgMåned) {
    /** Brukes for å deserialisere fra json */
    @JsonCreator
    constructor(fraOgMed: LocalDate, tilOgMed: LocalDate) : this(YearMonth.of(fraOgMed.year, fraOgMed.month)) {
        require(fraOgMed.year == tilOgMed.year) {
            "fraOgMed og tilOgMed må være innenfor samme år"
        }
        require(fraOgMed.month == tilOgMed.month) {
            "fraOgMed og tilOgMed må være innenfor samme måned"
        }
        validateOrThrow(fraOgMed, tilOgMed)
    }

    operator fun rangeTo(that: Måned): Periode {
        if (this == that) return this
        return create(this.fraOgMed, that.tilOgMed).also {
            require(this.før(that))
        }
    }

    /**
     * Returns a range from this value up to but excluding the specified to value.
     * If the to value is less than or equal to this value, then the returned range is empty.
     */
    fun until(endExclusive: Måned): List<Måned> {
        return (0 until this.årOgMåned.until(endExclusive.årOgMåned, ChronoUnit.MONTHS)).map {
            this.plusMonths(it)
        }
    }

    fun plusMonths(monthsToAdd: Long): Måned {
        return Måned(årOgMåned.plusMonths(monthsToAdd))
    }

    companion object {
        fun now(clock: Clock): Måned {
            return Måned(YearMonth.now(clock))
        }
    }

    override fun equals(other: Any?) = super.equals(other)
    override fun hashCode() = super.hashCode()
}

/**
 * @throws IllegalArgumentException dersom denne perioden er lengre enn 1 måned.
 */
fun Periode.tilMåned(): Måned {
    require(this.getAntallMåneder() == 1)
    return Måned(YearMonth.of(this.fraOgMed.year, this.fraOgMed.month))
}

/**
 * Mappet med måneder trenger ikke være sortert eller sammenhengende og kan ha duplikater.
 * @throws NoSuchElementException dersom mappet er tomt.
 */
fun <T> Map<Måned, T>.periode() = this.keys.toList().minAndMaxOf()
