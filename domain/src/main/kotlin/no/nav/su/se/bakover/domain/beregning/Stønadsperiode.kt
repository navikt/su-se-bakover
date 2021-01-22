package no.nav.su.se.bakover.domain.beregning

import arrow.core.Either
import arrow.core.getOrHandle
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.periode.Periode

data class Stønadsperiode private constructor(
    val periode: Periode
) {
    companion object {
        fun create(periode: Periode): Stønadsperiode {
            return tryCreate(periode).getOrHandle { throw IllegalArgumentException(it.toString()) }
        }

        fun tryCreate(periode: Periode): Either<UgyldigStønadsperiode, Stønadsperiode> {
            if (periode.getFraOgMed().year < 2021) { return UgyldigStønadsperiode.FraOgMedDatoKanIkkeVæreFør2021.left() }
            if (periode.getAntallMåneder() > 12) { return UgyldigStønadsperiode.PeriodeKanIkkeVæreLengreEnn12Måneder.left() }

            return Stønadsperiode(periode).right()
        }
    }

    sealed class UgyldigStønadsperiode {
        object PeriodeKanIkkeVæreLengreEnn12Måneder : UgyldigStønadsperiode()
        object FraOgMedDatoKanIkkeVæreFør2021 : UgyldigStønadsperiode()
    }
}
