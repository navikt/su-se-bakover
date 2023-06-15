package no.nav.su.se.bakover.domain.søknadsbehandling.stønadsperiode

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.tid.YearRange
import no.nav.su.se.bakover.common.tid.periode.Periode
import org.jetbrains.annotations.TestOnly
import java.time.Year

data class Stønadsperiode private constructor(
    val periode: Periode,
) : Comparable<Stønadsperiode> {

    infix fun inneholder(periode: Periode) = this.periode.inneholder(periode)
    infix fun inneholder(stønadsperiode: Stønadsperiode) = this.periode.inneholder(stønadsperiode.periode)

    companion object {

        @TestOnly
        fun create(periode: Periode): Stønadsperiode {
            return tryCreate(periode).getOrElse { throw IllegalArgumentException(it.toString()) }
        }

        fun tryCreate(periode: Periode): Either<UgyldigStønadsperiode, Stønadsperiode> {
            if (periode.fraOgMed.year < 2021) {
                return UgyldigStønadsperiode.FraOgMedDatoKanIkkeVæreFør2021.left()
            }
            if (periode.getAntallMåneder() > 12) {
                return UgyldigStønadsperiode.PeriodeKanIkkeVæreLengreEnn12Måneder.left()
            }

            return Stønadsperiode(periode).right()
        }
    }

    sealed interface UgyldigStønadsperiode {
        object PeriodeKanIkkeVæreLengreEnn12Måneder : UgyldigStønadsperiode
        object FraOgMedDatoKanIkkeVæreFør2021 : UgyldigStønadsperiode
    }

    override fun compareTo(other: Stønadsperiode) = periode.compareTo(other.periode)
    fun måneder() = periode.måneder()
    fun toYearRange(): YearRange = YearRange(Year.of(this.periode.fraOgMed.year), Year.of(this.periode.tilOgMed.year))
}
