package no.nav.su.se.bakover.common.tid.periode

import arrow.core.Either
import arrow.core.NonEmptyList
import arrow.core.left
import arrow.core.right
import java.time.LocalDate
import java.time.Period
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * Supertype for [Periode] som tillater at datoer ikke faller på første/siste dag i en måned.
 * Logikk som ikke er spesifikk for [Periode] kan flyttes hit.
 */
open class DatoIntervall(
    val fraOgMed: LocalDate,
    val tilOgMed: LocalDate,
) {

    init {
        require(fraOgMed <= tilOgMed) {
            "fraOgMed må være før eller samme dag som tilOgMed"
        }
    }

    constructor(dato: LocalDate) : this(dato, dato)

    infix fun inneholder(dato: LocalDate): Boolean = dato in fraOgMed..tilOgMed

    infix fun inneholder(other: DatoIntervall): Boolean =
        starterSamtidigEllerTidligere(other) && slutterSamtidigEllerSenere(other)

    infix fun inneholder(other: List<DatoIntervall>): Boolean {
        return other.all { this inneholder it }
    }

    /**
     * true: Det finnes minst en dag som overlapper
     * true: Fullstendig overlapp
     * true: equals
     * false: Det finnes ingen dager som overlapper
     */
    infix fun overlapper(other: List<DatoIntervall>): Boolean =
        other.any { this.overlapper(it) }

    /**
     * true: Det finnes minst en dag som overlapper
     * true: Fullstendig overlapp
     * true: equals
     * false: Det finnes ingen dager som overlapper
     */
    infix fun overlapper(other: DatoIntervall): Boolean =
        starterSamtidigEllerTidligere(other) && slutterInni(other) ||
            other.starterSamtidigEllerTidligere(this) && other.slutterInni(this) ||
            starterSamtidigEllerTidligere(other) && slutterEtter(other) ||
            other.starterSamtidigEllerTidligere(this) && other.slutterEtter(this)

    infix fun overlapperExcludingEndDate(other: DatoIntervall): Boolean {
        return this.fraOgMed < other.tilOgMed && this.tilOgMed > other.fraOgMed
    }

    infix fun starterEtter(dato: LocalDate): Boolean = tilOgMed.isAfter(dato)

    infix fun starterSamtidigEllerTidligere(other: DatoIntervall) = starterSamtidig(other) || starterTidligere(other)
    infix fun starterSamtidigEllerSenere(other: DatoIntervall) = starterSamtidig(other) || starterEtter(other)
    infix fun starterSamtidig(other: DatoIntervall) = fraOgMed.isEqual(other.fraOgMed)
    infix fun starterTidligere(other: DatoIntervall) = fraOgMed.isBefore(other.fraOgMed)
    infix fun starterEtter(other: DatoIntervall) = fraOgMed.isAfter(other.fraOgMed)

    infix fun slutterSamtidigEllerTidligere(other: DatoIntervall) = slutterSamtidig(other) || slutterTidligere(other)
    infix fun slutterSamtidigEllerSenere(other: DatoIntervall) = slutterSamtidig(other) || slutterEtter(other)
    infix fun slutterSamtidig(other: DatoIntervall) = tilOgMed.isEqual(other.tilOgMed)
    infix fun slutterTidligere(other: DatoIntervall) = tilOgMed.isBefore(other.tilOgMed)
    infix fun slutterEtter(other: DatoIntervall) = tilOgMed.isAfter(other.tilOgMed)
    infix fun slutterInni(other: DatoIntervall) = (starterSamtidigEllerTidligere(other) || starterEtter(other)) &&
        !før(other) && slutterSamtidigEllerTidligere(other)

    infix fun før(other: DatoIntervall) = tilOgMed.isBefore(other.fraOgMed)
    infix fun etter(other: DatoIntervall) = fraOgMed.isAfter(other.tilOgMed)

    /** Inkluderer første og siste dag. */
    fun antallDager(): Long = ChronoUnit.DAYS.between(fraOgMed, tilOgMed.plusDays(1))

    infix fun tilstøter(other: DatoIntervall): Boolean {
        val sluttStart = Period.between(tilOgMed, other.fraOgMed)
        val startSlutt = Period.between(fraOgMed, other.tilOgMed)
        val plussEnDag = Period.ofDays(1)
        val minusEnDag = Period.ofDays(-1)
        return this == other || sluttStart == plussEnDag || sluttStart == minusEnDag || startSlutt == plussEnDag || startSlutt == minusEnDag
    }

    infix fun tilstøter(other: LocalDate): Boolean {
        val dayBeforeStart = fraOgMed.minusDays(1)
        val dayAfterEnd = tilOgMed.plusDays(1)
        return other.isEqual(dayBeforeStart) || other.isEqual(dayAfterEnd)
    }

    infix fun slåSammen(
        other: DatoIntervall,
    ): Either<DatoIntervallKanIkkeSlåsSammen, DatoIntervall> {
        return slåSammen(other) { fraOgMed, tilOgMed -> DatoIntervall(fraOgMed, tilOgMed) }
    }

    infix fun slåSammen(
        other: LocalDate,
    ): Either<DatoIntervallKanIkkeSlåsSammen, DatoIntervall> {
        return slåSammen(DatoIntervall(other, other)) { fraOgMed, tilOgMed -> DatoIntervall(fraOgMed, tilOgMed) }
    }

    /**
     * Slår sammen to perioder dersom minst en måned overlapper eller periodene er tilstøtende.
     */
    fun <T : DatoIntervall> slåSammen(
        other: T,
        create: (fraOgMed: LocalDate, tilOgMed: LocalDate) -> T,
    ): Either<DatoIntervallKanIkkeSlåsSammen, T> {
        return if (overlapper(other) || tilstøter(other)) {
            create(
                minOf(this.fraOgMed, other.fraOgMed),
                maxOf(this.tilOgMed, other.tilOgMed),
            ).right()
        } else {
            DatoIntervallKanIkkeSlåsSammen.left()
        }
    }

    /**
     * @return Liste med alle dager i [DatoIntervall], sortert og unike.
     */
    fun dager(): List<LocalDate> {
        return (0 until antallDager()).map { fraOgMed.plusDays(it) }
    }

    /**
     * Periode i formatet "dd.MM.yyyy - dd.MM.yyyy".
     */
    fun ddMMyyyy(): String {
        val formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.of("nb", "NO"))
        return "${this.fraOgMed.format(formatter)} - ${this.tilOgMed.format(formatter)}"
    }

    override fun equals(other: Any?) =
        other is DatoIntervall && fraOgMed == other.fraOgMed && tilOgMed == other.tilOgMed

    override fun hashCode() = 31 * fraOgMed.hashCode() + tilOgMed.hashCode()

    override fun toString(): String {
        return "DatoIntervall(fraOgMed=$fraOgMed, tilOgMed=$tilOgMed)"
    }

    data object DatoIntervallKanIkkeSlåsSammen
}

/**
 * Finner minste antall sammenhengende datointervaller fra en liste med [DatoIntervall] ved å slå sammen elementer etter reglene
 * definert av [DatoIntervall.slåSammen].
 */
fun List<DatoIntervall>.minsteAntallSammenhengendeDatoIntervall(): List<DatoIntervall> {
    return this.dager()
        .fold(emptyList()) { acc, dato ->
            if (acc.isEmpty()) {
                listOf(DatoIntervall(dato, dato))
            } else {
                acc.last().slåSammen(dato).fold(
                    { acc + listOf(DatoIntervall(dato, dato)) },
                    { acc.dropLast(1) + it },
                )
            }.let {
                it.map {
                    DatoIntervall(it.fraOgMed, it.tilOgMed)
                }
            }
        }
}

infix fun List<DatoIntervall>.inneholder(other: List<DatoIntervall>): Boolean =
    other.minsteAntallSammenhengendeDatoIntervall().all { this.minsteAntallSammenhengendeDatoIntervall() inneholder it }

infix fun List<DatoIntervall>.inneholder(other: DatoIntervall): Boolean =
    this.minsteAntallSammenhengendeDatoIntervall().any { it inneholder other }

fun NonEmptyList<DatoIntervall>.minAndMaxOf(): DatoIntervall {
    return DatoIntervall(
        fraOgMed = this.minOf { it.fraOgMed },
        tilOgMed = this.maxOf { it.tilOgMed },
    )
}

fun List<DatoIntervall>.minAndMaxOfOrNull(): DatoIntervall? {
    if (this.isEmpty()) return null
    return DatoIntervall(
        fraOgMed = this.minOf { it.fraOgMed },
        tilOgMed = this.maxOf { it.tilOgMed },
    )
}

/**
 * @return Liste med alle dager i alle [DatoIntervall] i listen, sortert og unike.
 */
fun <T : DatoIntervall> List<T>.dager(): List<LocalDate> {
    return this.flatMap { it.dager() }.distinct().sorted()
}
