package no.nav.su.se.bakover.common.tid.periode

import arrow.core.Either
import com.fasterxml.jackson.annotation.JsonIgnore
import no.nav.su.se.bakover.common.domain.tid.erFørsteDagIMåned
import java.time.Clock
import java.time.LocalDate
import java.time.Month
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.time.temporal.Temporal
import java.time.temporal.TemporalAdjuster
import java.util.concurrent.ConcurrentHashMap

data class Måned private constructor(
    // Vi ønsker ikke ha denne i json enda, men holder oss til Periode sin fraOgMed og tilOgMed
    // TODO jah: Lag/bruk PeriodeJson for serialisering/deserialisering og fjern Jackson-referanser.
    @JsonIgnore
    val årOgMåned: YearMonth,
) : Periode(årOgMåned), Temporal by årOgMåned, TemporalAdjuster by årOgMåned, Comparable<Måned> {
    operator fun rangeTo(that: Måned): Periode {
        if (this == that) return this
        return create(this.fraOgMed, that.tilOgMed).also {
            require(this.før(that))
        }
    }
    // TODO - her vil vi ha en egen compareTo og comaprable på måned
    // override fun compareTo(other: Måned): Int = this.årOgMåned.compareTo(other.årOgMåned)

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
        return fra(årOgMåned.plusMonths(monthsToAdd))
    }

    fun tilPeriode(): Periode {
        return create(fraOgMed, tilOgMed)
    }

    companion object {
        private val factory = CacheingFactory()
        fun now(clock: Clock): Måned {
            return factory.fra(YearMonth.now(clock))
        }

        fun fra(yearMonth: YearMonth): Måned {
            return factory.fra(yearMonth)
        }

        fun fra(year: Int, month: Month): Måned {
            return factory.fra(YearMonth.of(year, month))
        }

        /**
         * @throws IllegalArgumentException dersom dato ikke er den 1. i måneden.
         */
        fun fra(dato: LocalDate): Måned {
            require(dato.erFørsteDagIMåned()) {
                "$dato må være den 1. i måneden for å mappes til en måned."
            }
            return factory.fra(YearMonth.of(dato.year, dato.month))
        }

        fun fra(fraOgMed: LocalDate, tilOgMed: LocalDate): Måned {
            return factory.fra(fraOgMed, tilOgMed)
        }

        fun parse(value: String): Måned? {
            return Either.catch {
                factory.fra(YearMonth.parse(value))
            }.getOrNull()
        }

        private class CacheingFactory(
            private val cached: MutableMap<YearMonth, Måned> = ConcurrentHashMap(48),
        ) {
            fun fra(yearMonth: YearMonth): Måned {
                return cached.getOrPut(yearMonth) { Måned(yearMonth) }
            }

            fun fra(fraOgMed: LocalDate, tilOgMed: LocalDate): Måned {
                require(fraOgMed.year == tilOgMed.year) {
                    "fraOgMed og tilOgMed må være innenfor samme år"
                }
                require(fraOgMed.month == tilOgMed.month) {
                    "fraOgMed og tilOgMed må være innenfor samme måned"
                }
                validateOrThrow(fraOgMed, tilOgMed)
                return fra(YearMonth.of(fraOgMed.year, fraOgMed.month))
            }
        }
    }

    /** Siden [Periode] og [DatoIntervall] kan delvis overlappe, er det unaturlig å sammenligne de med hverandre og med måned. */
    override fun compareTo(other: Måned) = this.årOgMåned.compareTo(other.årOgMåned)

    /** Vi ønsker samme equals som [Periode]. */
    override fun equals(other: Any?) = super.equals(other)

    /** Vi ønsker samme hashCode som [Periode]. */
    override fun hashCode() = super.hashCode()
    override fun toString() = årOgMåned.toString()
}

/**
 * @throws IllegalArgumentException dersom denne perioden er lengre enn 1 måned.
 */
fun Periode.tilMåned(): Måned {
    require(this.getAntallMåneder() == 1)
    return Måned.fra(YearMonth.of(this.fraOgMed.year, this.fraOgMed.month))
}

/**
 * Mappet med måneder trenger ikke være sortert eller sammenhengende og kan ha duplikater.
 * @throws NoSuchElementException dersom mappet er tomt.
 */
fun <T> Map<Måned, T>.periode() = this.keys.toList().minAndMaxOf()

fun YearMonth.tilMåned() = Måned.fra(this)

/**
 * @throws IllegalArgumentException dersom dato ikke er den 1. i måneden.
 */
fun LocalDate.toMåned(): Måned = Måned.fra(this)

fun januar(year: Int) = Måned.fra(YearMonth.of(year, Month.JANUARY))
fun februar(year: Int) = Måned.fra(YearMonth.of(year, Month.FEBRUARY))
fun mars(year: Int) = Måned.fra(YearMonth.of(year, Month.MARCH))
fun april(year: Int) = Måned.fra(YearMonth.of(year, Month.APRIL))
fun mai(year: Int) = Måned.fra(YearMonth.of(year, Month.MAY))
fun juni(year: Int) = Måned.fra(YearMonth.of(year, Month.JUNE))
fun juli(year: Int) = Måned.fra(YearMonth.of(year, Month.JULY))
fun august(year: Int) = Måned.fra(YearMonth.of(year, Month.AUGUST))
fun september(year: Int) = Måned.fra(YearMonth.of(year, Month.SEPTEMBER))
fun oktober(year: Int) = Måned.fra(YearMonth.of(year, Month.OCTOBER))
fun november(year: Int) = Måned.fra(YearMonth.of(year, Month.NOVEMBER))
fun desember(year: Int) = Måned.fra(YearMonth.of(year, Month.DECEMBER))

fun List<Måned>.erLikEllerTilstøtende(other: Måned): Boolean {
    return this.any { it == other || it.årOgMåned.plusMonths(1) == other.årOgMåned || it.årOgMåned.minusMonths(1) == other.årOgMåned }
}
