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
