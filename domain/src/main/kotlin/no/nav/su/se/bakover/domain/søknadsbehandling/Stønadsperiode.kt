package no.nav.su.se.bakover.domain.søknadsbehandling

import arrow.core.Either
import arrow.core.getOrHandle
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.periode.Periode
import org.jetbrains.annotations.TestOnly

data class Stønadsperiode private constructor(
    val periode: Periode,
    val begrunnelse: String,
) : Comparable<Stønadsperiode> {

    infix fun inneholder(periode: Periode) = this.periode.inneholder(periode)
    infix fun inneholder(stønadsperiode: Stønadsperiode) = this.periode.inneholder(stønadsperiode.periode)

    companion object {

        @TestOnly
        fun create(periode: Periode, begrunnelse: String = ""): Stønadsperiode {
            return tryCreate(periode, begrunnelse).getOrHandle { throw IllegalArgumentException(it.toString()) }
        }

        fun tryCreate(periode: Periode, begrunnelse: String): Either<UgyldigStønadsperiode, Stønadsperiode> {
            if (periode.fraOgMed.year < 2021) {
                return UgyldigStønadsperiode.FraOgMedDatoKanIkkeVæreFør2021.left()
            }
            if (periode.getAntallMåneder() > 12) {
                return UgyldigStønadsperiode.PeriodeKanIkkeVæreLengreEnn12Måneder.left()
            }

            return Stønadsperiode(periode, begrunnelse).right()
        }
    }

    sealed class UgyldigStønadsperiode {
        object PeriodeKanIkkeVæreLengreEnn12Måneder : UgyldigStønadsperiode()
        object FraOgMedDatoKanIkkeVæreFør2021 : UgyldigStønadsperiode()
    }

    override fun compareTo(other: Stønadsperiode) = periode.compareTo(other.periode)
}
