package no.nav.su.se.bakover.common.domain.tid.periode

import arrow.core.NonEmptyList
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.common.tid.periode.harOverlappende
import java.time.LocalDate

/**
 * Krever at vi har minst én periode.
 * Periodene kan overlappe, det kan være hull og rekkefølgen kan være vilkårlig.
 */
sealed interface NonEmptyPerioder : Perioder {
    override val perioder: NonEmptyList<Periode>
    override val fraOgMed: LocalDate get() = perioder.minOf { it.fraOgMed }
    override val tilOgMed: LocalDate get() = perioder.maxOf { it.tilOgMed }

    companion object {
        fun create(perioder: NonEmptyList<Periode>): NonEmptyPerioder {
            return when {
                perioder.size == 1 -> NonEmptySlåttSammenIkkeOverlappendePerioder.create(perioder)
                else -> {
                    if (perioder.harOverlappende()) {
                        NonEmptyOverlappendePerioder.create(perioder)
                    } else {
                        IkkeOverlappendePerioder.create(perioder)
                    }
                }
            }
        }
    }
}
