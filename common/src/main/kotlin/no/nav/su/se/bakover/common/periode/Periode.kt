package no.nav.su.se.bakover.common.periode

import arrow.core.Either
import arrow.core.getOrHandle
import arrow.core.left
import arrow.core.right
import com.fasterxml.jackson.annotation.JsonIgnore
import no.nav.su.se.bakover.common.erFørsteDagIMåned
import no.nav.su.se.bakover.common.erSisteDagIMåned
import java.time.LocalDate
import java.time.Period

data class Periode private constructor(
    private val fraOgMed: LocalDate,
    private val tilOgMed: LocalDate
) {
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

    infix fun overlapper(other: Periode): Boolean =
        starterSamtidigEllerTidligere(other) && slutterInni(other) ||
            other.starterSamtidigEllerTidligere(this) && other.slutterInni(this) ||
            starterSamtidigEllerTidligere(other) && slutterEtter(other) ||
            other.starterSamtidigEllerTidligere(this) && other.slutterEtter(this)

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

    companion object {
        fun create(fraOgMed: LocalDate, tilOgMed: LocalDate): Periode {
            return tryCreate(fraOgMed, tilOgMed).getOrHandle { throw IllegalArgumentException(it.toString()) }
        }

        fun tryCreate(fraOgMed: LocalDate, tilOgMed: LocalDate): Either<UgyldigPeriode, Periode> {
            if (!fraOgMed.erFørsteDagIMåned()) { return UgyldigPeriode.FraOgMedDatoMåVæreFørsteDagIMåneden.left() }
            if (!tilOgMed.erSisteDagIMåned()) { return UgyldigPeriode.TilOgMedDatoMåVæreSisteDagIMåneden.left() }
            if (!fraOgMed.isBefore(tilOgMed)) { return UgyldigPeriode.FraOgMedDatoMåVæreFørTilOgMedDato.left() }

            return Periode(fraOgMed, tilOgMed).right()
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
}
