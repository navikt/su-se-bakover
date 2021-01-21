package no.nav.su.se.bakover.common.periode

import arrow.core.Either
import arrow.core.getOrHandle
import arrow.core.left
import arrow.core.right
import com.fasterxml.jackson.annotation.JsonIgnore
import java.time.LocalDate
import java.time.Period

data class Periode private constructor(
    private val fraOgMed: LocalDate,
    private val tilOgMed: LocalDate
) {
    fun getFraOgMed() = fraOgMed
    fun getTilOgMed() = tilOgMed

    @JsonIgnore
    fun getAntallMåneder() = Companion.getAntallMåneder(fraOgMed, tilOgMed)
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
            //TODO Denne ønsker vi at skal være 2021, men pga veldig mange tester på Grunnbeløp, så kan vi ikke endre denne enda
            if (fraOgMed.year < 2020) { return UgyldigPeriode.FraOgMedDatoKanIkkeVæreFør2020.left() }
            if (fraOgMed.dayOfMonth != 1) { return UgyldigPeriode.FraOgMedDatoMåVæreFørsteDagIMåneden.left() }
            if (tilOgMed.dayOfMonth != tilOgMed.lengthOfMonth()) { return UgyldigPeriode.TilOgMedDatoMåVæreSisteDagIMåneden.left() }
            if (!fraOgMed.isBefore(tilOgMed)) { return UgyldigPeriode.FraOgMedDatoMåVæreFørTilOgMedDato.left() }
            if (getAntallMåneder(fraOgMed, tilOgMed) > 12) { return UgyldigPeriode.PeriodeKanIkkeVæreLengreEnn12Måneder.left() }

            return Periode(fraOgMed, tilOgMed).right()
        }

        private fun getAntallMåneder(fraOgMed: LocalDate, tilOgMed: LocalDate) = Period.between(fraOgMed, tilOgMed.plusDays(1)).toTotalMonths().toInt()
    }

    sealed class UgyldigPeriode {
        object FraOgMedDatoMåVæreFørsteDagIMåneden : UgyldigPeriode()
        object TilOgMedDatoMåVæreSisteDagIMåneden : UgyldigPeriode()
        object FraOgMedDatoMåVæreFørTilOgMedDato : UgyldigPeriode()
        object PeriodeKanIkkeVæreLengreEnn12Måneder : UgyldigPeriode()
        object FraOgMedDatoKanIkkeVæreFør2020 : UgyldigPeriode()
    }
}
