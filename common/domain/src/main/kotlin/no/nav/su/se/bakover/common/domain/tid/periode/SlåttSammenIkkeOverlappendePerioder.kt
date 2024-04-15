package no.nav.su.se.bakover.common.domain.tid.periode

import no.nav.su.se.bakover.common.domain.extensions.toNonEmptyList
import no.nav.su.se.bakover.common.tid.periode.Periode

/**
 * En sortert liste med perioder som ikke overlapper og er minste sammenhengende (tilstøtende perioder er slått sammen).
 * Dette er en sterkere garanti enn [IkkeOverlappendePerioder], og kan brukes på steder hvor det er viktig at periodene ikke overlapper, f.eks. sortering.
 *
 * Periodene kan fremdeles ha hull.
 */
sealed interface SlåttSammenIkkeOverlappendePerioder : IkkeOverlappendePerioder {

    companion object {
        fun create(perioder: List<Periode>): SlåttSammenIkkeOverlappendePerioder {
            if (perioder.isEmpty()) return EmptyPerioder
            return NonEmptySlåttSammenIkkeOverlappendePerioder.create(perioder.toNonEmptyList())
        }
    }
}
