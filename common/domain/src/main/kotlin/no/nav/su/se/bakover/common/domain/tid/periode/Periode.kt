package no.nav.su.se.bakover.common.tid.periode

import arrow.core.Either
import arrow.core.Nel
import arrow.core.NonEmptyList
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import com.fasterxml.jackson.annotation.JsonIgnore
import no.nav.su.se.bakover.common.extensions.endOfMonth
import no.nav.su.se.bakover.common.extensions.erFørsteDagIMåned
import no.nav.su.se.bakover.common.extensions.erSisteDagIMåned
import no.nav.su.se.bakover.common.extensions.startOfMonth
import no.nav.su.se.bakover.common.extensions.toNonEmptyList
import java.time.LocalDate
import java.time.Month
import java.time.Period
import java.time.YearMonth
import kotlin.collections.minus as setsMinus

/**
 * TODO jah: Bør lage en Json-versjon, domenetyper skal ikke serialiseres/deserialiseres direkte.
 */
open class Periode protected constructor(
    fraOgMed: LocalDate,
    tilOgMed: LocalDate,
) : DatoIntervall(fraOgMed, tilOgMed) {

    constructor(måned: YearMonth) : this(måned.atDay(1), måned.atEndOfMonth()) {
        validateOrThrow(fraOgMed, tilOgMed)
    }

    init {
        validateOrThrow(fraOgMed, tilOgMed)
    }

    /**
     * @throws IllegalStateException dersom fraOgMed er LocalDate.MIN eller tilOgMed er LocalDate.MAX
     * @throws ArithmeticException dersom antall måneder er større enn en Int.
     */
    @JsonIgnore
    fun getAntallMåneder(): Int {
        if (fraOgMed == LocalDate.MIN || tilOgMed == LocalDate.MAX) {
            throw IllegalStateException("Var dette meningen? Unngår å loope fra LocalDate.MIN og/eller til LocalDate.MAX. fraOgMed: $fraOgMed, tilOgMed: $tilOgMed ")
        }
        return Period.between(fraOgMed, tilOgMed.plusDays(1)).toTotalMonths().let {
            Math.toIntExact(it)
        }
    }

    /**
     * @throws IllegalStateException dersom fraOgMed er LocalDate.MIN eller tilOgMed er LocalDate.MAX
     * @throws ArithmeticException dersom antall måneder er større enn en Int.
     */
    fun måneder(): NonEmptyList<Måned> {
        return (0L until getAntallMåneder()).map {
            val currentMonth = fraOgMed.plusMonths(it)
            Måned.fra(YearMonth.of(currentMonth.year, currentMonth.month))
        }.toNonEmptyList()
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
     * Perioden som overlapper begge perioder eller ingenting hvis periodene ikke overlapper i det heletatt. (se mengdelære).
     */
    infix fun snitt(other: Periode): Periode? {
        return if (this overlapper other) {
            create(
                fraOgMed = maxOf(this.fraOgMed, other.fraOgMed),
                tilOgMed = minOf(this.tilOgMed, other.tilOgMed),
            )
        } else {
            null
        }
    }

    infix operator fun minus(other: Periode): List<Periode> {
        return (måneder() - other.måneder().toSet()).minsteAntallSammenhengendePerioder()
    }

    infix operator fun minus(other: Collection<Periode>): List<Periode> {
        return (måneder() - other.toList().måneder().toSet()).minsteAntallSammenhengendePerioder()
    }

    infix fun slåSammen(
        other: Periode,
    ): Either<PerioderKanIkkeSlåsSammen, Periode> {
        return slåSammen(other) { fraOgMed, tilOgMed ->
            create(
                fraOgMed,
                tilOgMed,
            )
        }.mapLeft { PerioderKanIkkeSlåsSammen }
    }

    data object PerioderKanIkkeSlåsSammen

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

        fun create(fraOgMed: LocalDate, tilOgMed: LocalDate): Periode = tryCreate(fraOgMed, tilOgMed).getOrElse {
            throw IllegalArgumentException(
                "Perioder må være fra første til siste i måneden. fraOgMed må være før tilOgMed, men var: fraOgMed: $fraOgMed, tilOgMed: $tilOgMed",
            )
        }

        fun tryCreate(fraOgMed: LocalDate, tilOgMed: LocalDate): Either<UgyldigPeriode, Periode> =
            validate(fraOgMed, tilOgMed).map { Periode(fraOgMed, tilOgMed) }

        @JvmStatic
        protected fun validateOrThrow(fraOgMed: LocalDate, tilOgMed: LocalDate) {
            validate(fraOgMed, tilOgMed).onLeft {
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

    sealed interface UgyldigPeriode {
        data object FraOgMedDatoMåVæreFørsteDagIMåneden : UgyldigPeriode

        data object TilOgMedDatoMåVæreSisteDagIMåneden : UgyldigPeriode

        data object FraOgMedDatoMåVæreFørTilOgMedDato : UgyldigPeriode
    }

    override fun equals(other: Any?) = super.equals(other)

    override fun hashCode() = super.hashCode()

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

fun NonEmptyList<Periode>.minAndMaxOf(): Periode {
    return Periode.create(
        fraOgMed = this.minOf { it.fraOgMed },
        tilOgMed = this.maxOf { it.tilOgMed },
    )
}

fun List<Periode>.minAndMaxOfOrNull(): Periode? {
    if (this.isEmpty()) return null
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
    return this.måneder().fold(emptyList()) { acc, måned ->
        if (acc.isEmpty()) {
            listOf(måned.tilPeriode())
        } else {
            acc.last().slåSammen(måned.tilPeriode()).fold(
                { acc + måned.tilPeriode() },
                { acc.dropLast(1) + it },
            )
        }
    }
}

fun Nel<Periode>.minsteAntallSammenhengendePerioder(): Nel<Periode> {
    return (this as List<Periode>).minsteAntallSammenhengendePerioder().toNonEmptyList()
}

/**
 * Fjerner alle periodene inneholdt i [other] fra [this]. Eliminerer duplikater og slår sammen gjenstående
 * perioder i [this] til en minimum antall sammenhengende perioder.
 */
infix operator fun Iterable<Periode>.minus(other: Iterable<Periode>): List<Periode> {
    return (flatMap { it.måneder() }.toSet().setsMinus(other.flatMap { it.måneder() }.toSet()))
        .toList()
        .minsteAntallSammenhengendePerioder()
}

infix operator fun Iterable<Periode>.minus(other: Periode): List<Periode> {
    return (flatMap { it.måneder() }.toSet() - other.måneder())
        .toList()
        .minsteAntallSammenhengendePerioder()
}

/**
 * Listen med perioder trenger ikke være sortert eller sammenhengende og kan ha duplikater.
 *
 * @return En sortert liste med måneder uten duplikater som kan være usammenhengende.
 */
fun Collection<Periode>.måneder(): List<Måned> {
    if (this.isEmpty()) return emptyList()
    return this.toList().toNonEmptyList().måneder()
}

/**
 * Listen med perioder trenger ikke være sortert eller sammenhengende og kan ha duplikater.
 *
 * @return En sortert liste med måneder uten duplikater som kan være usammenhengende.
 */
fun NonEmptyList<Periode>.måneder(): NonEmptyList<Måned> {
    return this.flatMap { it.måneder() }.distinct().sorted().toNonEmptyList()
}

/**
 * Sjekker om periodene er sortert basert på fraOgMed.
 * Listen med perioder kan være usammenhengende og ha duplikator.
 * Dersom den har overlappende perioder, gir ikke denne sjekken veldig mye mening. Bør brukes i sammenheng med [harOverlappende].
 */
fun List<Periode>.erSortertPåFraOgMed(): Boolean {
    return this.map { it.fraOgMed }.sorted() == this.map { it.fraOgMed }
}

/**
 * Sjekker om periodene er sortert basert på fraOgMed og deretter tilOgMed.
 * Listen med perioder kan være usammenhengende og ha duplikator.
 * Dersom den har overlappende perioder, gir ikke denne sjekken veldig mye mening. Bør brukes i sammenheng med [harOverlappende].
 */
fun List<Periode>.erSortertPåFraOgMedDeretterTilOgMed(): Boolean {
    return this == this.sorterPåFraOgMedDeretterTilOgMed()
}

/**
 * Sorterer periodene først på fraOgMed og deretter tilOgMed.
 * Listen med perioder kan være usammenhengende og ha duplikator.
 * Dersom den har overlappende perioder, gir ikke denne sjekken veldig mye mening. Bør brukes i sammenheng med [harOverlappende].
 */
fun List<Periode>.sorterPåFraOgMedDeretterTilOgMed(): List<Periode> {
    return this.sortedWith(compareBy<Periode> { it.fraOgMed }.thenBy { it.tilOgMed })
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
 *
 * @throws IllegalStateException dersom fraOgMed er LocalDate.MIN eller tilOgMed er LocalDate.MAX
 * @throws ArithmeticException dersom antall måneder er større enn en Int.
 */
fun List<Periode>.erSammenhengende(): Boolean {
    return if (this.isEmpty()) {
        true
    } else {
        this.flatMap { it.måneder() }.distinct().size == this.toNonEmptyList().minAndMaxOf()
            .getAntallMåneder()
    }
}

/**
 * Sjekker om en liste med perioder er sammenhengende, sortert og uten duplikater.
 *
 * @throws IllegalStateException dersom fraOgMed er LocalDate.MIN eller tilOgMed er LocalDate.MAX
 * @throws ArithmeticException dersom antall måneder er større enn en Int.
 */
fun List<Periode>.erSammenhengendeSortertOgUtenDuplikater(): Boolean {
    return erSammenhengende() && !harDuplikater() && erSortertPåFraOgMed()
}

fun <T> Map<Måned, T>.erSammenhengendeSortertOgUtenDuplikater(): Boolean {
    return this.keys.toList().erSammenhengendeSortertOgUtenDuplikater()
}

fun List<Periode>.harOverlappende(): Boolean {
    return this.flatMap { it.måneder() } != this.flatMap { it.måneder() }.distinct()
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
