package no.nav.su.se.bakover.common.periode

import arrow.core.Either
import arrow.core.Nel
import arrow.core.NonEmptyList
import arrow.core.getOrHandle
import arrow.core.left
import arrow.core.right
import com.fasterxml.jackson.annotation.JsonIgnore
import no.nav.su.se.bakover.common.endOfMonth
import no.nav.su.se.bakover.common.erFørsteDagIMåned
import no.nav.su.se.bakover.common.erSisteDagIMåned
import no.nav.su.se.bakover.common.startOfMonth
import java.time.LocalDate
import java.time.Month
import java.time.Period
import java.time.YearMonth

open class Periode protected constructor(
    val fraOgMed: LocalDate,
    val tilOgMed: LocalDate,
) : Comparable<Periode> {

    constructor(måned: YearMonth) : this(måned.atDay(1), måned.atEndOfMonth()) {
        validateOrThrow(fraOgMed, tilOgMed)
    }

    @JsonIgnore
    fun getAntallMåneder(): Int = Period.between(fraOgMed, tilOgMed.plusDays(1)).toTotalMonths().toInt()

    fun måneder(): NonEmptyList<Måned> {
        return NonEmptyList.fromListUnsafe(
            (0L until getAntallMåneder()).map {
                val currentMonth = fraOgMed.plusMonths(it)
                Måned.fra(YearMonth.of(currentMonth.year, currentMonth.month))
            },
        )
    }

    infix fun inneholder(other: Periode): Boolean =
        starterSamtidigEllerTidligere(other) && slutterSamtidigEllerSenere(other)

    infix fun inneholder(dato: LocalDate): Boolean =
        dato in fraOgMed..tilOgMed

    infix fun starterEtter(dato: LocalDate): Boolean = tilOgMed.isAfter(dato)

    infix fun tilstøter(other: Periode): Boolean {
        val sluttStart = Period.between(tilOgMed, other.fraOgMed)
        val startSlutt = Period.between(fraOgMed, other.tilOgMed)
        val plussEnDag = Period.ofDays(1)
        val minusEnDag = Period.ofDays(-1)
        return this == other || sluttStart == plussEnDag || sluttStart == minusEnDag || startSlutt == plussEnDag || startSlutt == minusEnDag
    }

    infix fun fullstendigOverlapp(other: Periode): Boolean =
        this fullstendigOverlapp listOf(other)

    /**
     * Alle månedene i denne perioden overlapper fullstendig med settet av alle månedene i lista.
     * Dvs. at de må inneholde de nøyaktige samme måneder.
     */
    infix fun fullstendigOverlapp(other: List<Periode>): Boolean =
        this.måneder().toSet() == other.flatMap { it.måneder() }.toSet()

    /**
     * true: Det finnes minst en måned som overlapper
     * true: Fullstendig overlapp
     * true: equals
     * false: Det finnes ingen måneder som overlapper
     */
    infix fun overlapper(other: List<Periode>): Boolean =
        other.any { this.overlapper(it) }

    /**
     * true: Det finnes minst en måned som overlapper
     * true: Fullstendig overlapp
     * true: equals
     * false: Det finnes ingen måneder som overlapper
     */
    infix fun overlapper(other: Periode): Boolean =
        starterSamtidigEllerTidligere(other) && slutterInni(other) ||
            other.starterSamtidigEllerTidligere(this) && other.slutterInni(this) ||
            starterSamtidigEllerTidligere(other) && slutterEtter(other) ||
            other.starterSamtidigEllerTidligere(this) && other.slutterEtter(this)

    /**
     * Perioden som overlapper begge perioder eller ingenting hvis periodene ikke overlapper i det heletatt. (se mengdelære).
     */
    infix fun snitt(other: Periode): Periode? {
        return if (this overlapper other) create(
            fraOgMed = maxOf(this.fraOgMed, other.fraOgMed),
            tilOgMed = minOf(this.tilOgMed, other.tilOgMed),
        ) else null
    }

    /**
     * Slår sammen to perioder dersom minst en måned overlapper eller periodene er tilstøtende.
     */
    infix fun slåSammen(other: Periode): Either<PerioderKanIkkeSlåsSammen, Periode> {
        return if (overlapper(other) || tilstøter(other)) {
            create(
                fraOgMed = minOf(this.fraOgMed, other.fraOgMed),
                tilOgMed = maxOf(this.tilOgMed, other.tilOgMed),
            ).right()
        } else {
            PerioderKanIkkeSlåsSammen.left()
        }
    }

    object PerioderKanIkkeSlåsSammen

    infix fun starterSamtidigEllerTidligere(other: Periode) = starterSamtidig(other) || starterTidligere(other)
    infix fun starterSamtidigEllerSenere(other: Periode) = starterSamtidig(other) || starterEtter(other)
    infix fun starterSamtidig(other: Periode) = fraOgMed.isEqual(other.fraOgMed)
    infix fun starterTidligere(other: Periode) = fraOgMed.isBefore(other.fraOgMed)
    infix fun starterEtter(other: Periode) = fraOgMed.isAfter(other.fraOgMed)

    infix fun slutterSamtidigEllerTidligere(other: Periode) = slutterSamtidig(other) || slutterTidligere(other)
    infix fun slutterSamtidigEllerSenere(other: Periode) = slutterSamtidig(other) || slutterEtter(other)
    infix fun slutterSamtidig(other: Periode) = tilOgMed.isEqual(other.tilOgMed)
    infix fun slutterTidligere(other: Periode) = tilOgMed.isBefore(other.tilOgMed)
    infix fun slutterEtter(other: Periode) = tilOgMed.isAfter(other.tilOgMed)
    infix fun slutterInni(other: Periode) = (starterSamtidigEllerTidligere(other) || starterEtter(other)) &&
        !før(other) && slutterSamtidigEllerTidligere(other)

    infix fun før(other: Periode) = tilOgMed.isBefore(other.fraOgMed)
    infix fun etter(other: Periode) = fraOgMed.isAfter(other.tilOgMed)
    infix fun minus(other: Periode): List<Periode> {
        return (måneder() - other.måneder().toSet()).minsteAntallSammenhengendePerioder()
    }

    /**
     * Forskyver en periode n hele måneder angitt av parameteret [måneder].
     * Positivt heltall er framover i tid, negativt heltall er bakover i tid.
     */
    fun forskyv(måneder: Int): Periode {
        return Periode(
            fraOgMed.plusMonths(måneder.toLong()).startOfMonth(),
            tilOgMed.plusMonths(måneder.toLong()).endOfMonth(),
        )
    }

    companion object {

        fun create(fraOgMed: LocalDate, tilOgMed: LocalDate): Periode {
            return tryCreate(fraOgMed, tilOgMed).getOrHandle { throw IllegalArgumentException(it.toString()) }
        }

        fun tryCreate(fraOgMed: LocalDate, tilOgMed: LocalDate): Either<UgyldigPeriode, Periode> {
            return validate(fraOgMed, tilOgMed).map {
                Periode(fraOgMed, tilOgMed)
            }
        }

        @JvmStatic
        protected fun validateOrThrow(fraOgMed: LocalDate, tilOgMed: LocalDate) {
            validate(fraOgMed, tilOgMed).tapLeft {
                throw IllegalArgumentException(it.toString())
            }
        }

        private fun validate(fraOgMed: LocalDate, tilOgMed: LocalDate): Either<UgyldigPeriode, Unit> {
            if (!fraOgMed.erFørsteDagIMåned()) {
                return UgyldigPeriode.FraOgMedDatoMåVæreFørsteDagIMåneden.left()
            }
            if (!tilOgMed.erSisteDagIMåned()) {
                return UgyldigPeriode.TilOgMedDatoMåVæreSisteDagIMåneden.left()
            }
            if (!fraOgMed.isBefore(tilOgMed)) {
                return UgyldigPeriode.FraOgMedDatoMåVæreFørTilOgMedDato.left()
            }
            return Unit.right()
        }
    }

    sealed class UgyldigPeriode {
        object FraOgMedDatoMåVæreFørsteDagIMåneden : UgyldigPeriode() {
            override fun toString(): String = this.javaClass.simpleName
        }

        object TilOgMedDatoMåVæreSisteDagIMåneden : UgyldigPeriode() {
            override fun toString(): String = this.javaClass.simpleName
        }

        object FraOgMedDatoMåVæreFørTilOgMedDato : UgyldigPeriode() {
            override fun toString(): String = this.javaClass.simpleName
        }
    }

    override fun compareTo(other: Periode) = fraOgMed.compareTo(other.fraOgMed)

    override fun equals(other: Any?) = other is Periode && fraOgMed == other.fraOgMed && tilOgMed == other.tilOgMed

    override fun hashCode() = 31 * fraOgMed.hashCode() + tilOgMed.hashCode()

    override fun toString(): String {
        return "Periode(fraOgMed=$fraOgMed, tilOgMed=$tilOgMed)"
    }
}

/**
 * Aksepterer at lista er usortert og usammenhengende.
 * @throws IndexOutOfBoundsException dersom lista med periode er tom.
 */
fun List<Periode>.minAndMaxOf(): Periode {
    return Periode.create(
        fraOgMed = this.minOf { it.fraOgMed },
        tilOgMed = this.maxOf { it.tilOgMed },
    )
}

/**
 * Finner minste antall sammenhengende perioder fra en liste med [Periode] ved å slå sammen elementer etter reglene
 * definert av [Periode.slåSammen].
 */
fun List<Periode>.minsteAntallSammenhengendePerioder(): List<Periode> {
    return sorted().fold(mutableListOf()) { slåttSammen: MutableList<Periode>, periode: Periode ->
        if (slåttSammen.isEmpty()) {
            slåttSammen.add(periode)
        } else if (slåttSammen.last().slåSammen(periode).isRight()) {
            val last = slåttSammen.removeLast()
            slåttSammen.add(last.slåSammen(periode).getOrHandle { throw IllegalStateException("Skulle gått bra") })
        } else {
            slåttSammen.add(periode)
        }
        slåttSammen
    }
}

fun Nel<Periode>.minsteAntallSammenhengendePerioder(): Nel<Periode> {
    return (this as List<Periode>).minsteAntallSammenhengendePerioder().let {
        Nel.fromListUnsafe(it)
    }
}

/**
 * Fjerner alle periodene inneholdt i [other] fra [this]. Eliminerer duplikater og slår sammen gjenstående
 * perioder i [this] til en minimum antall sammenhengende perioder.
 */
operator fun List<Periode>.minus(other: List<Periode>): List<Periode> {
    return (flatMap { it.måneder() }.toSet() - other.flatMap { it.måneder() }.toSet())
        .toList()
        .minsteAntallSammenhengendePerioder()
}

fun Periode.inneholderAlle(other: List<Periode>): Boolean {
    return måneder().inneholderAlle(other)
}

fun List<Periode>.inneholderAlle(other: List<Periode>): Boolean {
    val denne = flatMap { it.måneder() }.toSet()
    val andre = other.flatMap { it.måneder() }.toSet()
    return when {
        other.isEmpty() -> {
            true
        }
        denne.count() >= andre.count() -> {
            (andre - denne).isEmpty()
        }
        else -> {
            false
        }
    }
}

/**
 * Listen med perioder trenger ikke være sortert eller sammenhengende og kan ha duplikater.
 *
 * @return En sortert liste med måneder uten duplikater som kan være usammenhengende.
 */
fun List<Periode>.måneder(): List<Måned> {
    if (this.isEmpty()) return emptyList()
    return Nel.fromListUnsafe(this).måneder()
}

/**
 * Listen med perioder trenger ikke være sortert eller sammenhengende og kan ha duplikater.
 *
 * @return En sortert liste med måneder uten duplikater som kan være usammenhengende.
 */
fun NonEmptyList<Periode>.måneder(): NonEmptyList<Måned> {
    return Nel.fromListUnsafe(this.flatMap { it.måneder() }.distinct().sorted())
}

/**
 * Sjekker om periodene er sortert.
 * Listen med perioder kan være usammenhengende og ha duplikator.
 */
fun List<Periode>.erSortert(): Boolean {
    return this.sorted() == this
}

/**
 * Sjekker om en liste med perioder har duplikater.
 * Listen trenger ikke være sortert og kan være usammenhengende.
 */
fun List<Periode>.harDuplikater(): Boolean {
    return this.flatMap { it.måneder() }.let {
        it.distinct().size != it.size
    }
}

/**
 * Sjekker om det ikke er hull i periodene.
 * Listen med perioder trenger ikke å være sortert og kan inneholde duplikater
 * En tom liste gir `true`
 */
fun List<Periode>.erSammenhengende(): Boolean {
    return if (this.isEmpty()) true
    else this.flatMap { it.måneder() }.distinct().size == NonEmptyList.fromListUnsafe(this).minAndMaxOf()
        .getAntallMåneder()
}

/**
 * Sjekker om en liste med perioder er sammenhengende, sortert og uten duplikater.
 */
fun List<Periode>.erSammenhengendeSortertOgUtenDuplikater(): Boolean {
    return erSammenhengende() && erSortert() && !harDuplikater()
}

fun <T> Map<Måned, T>.erSammenhengendeSortertOgUtenDuplikater(): Boolean {
    return this.keys.toList().erSammenhengendeSortertOgUtenDuplikater()
}

fun List<Periode>.harOverlappende(): Boolean {
    return if (isEmpty()) false else this.any { p1 -> this.minus(p1).any { p2 -> p1 overlapper p2 } }
}

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
fun år(year: Int) = Periode.create(
    fraOgMed = YearMonth.of(year, Month.JANUARY).atDay(1),
    tilOgMed = YearMonth.of(year, Month.DECEMBER).atEndOfMonth(),
)
