package no.nav.su.se.bakover.common.domain.tid.periode

import arrow.core.NonEmptyList
import no.nav.su.se.bakover.common.domain.extensions.toNonEmptyList
import no.nav.su.se.bakover.common.domain.tid.periode.EmptyPerioder.minsteAntallSammenhengendePerioder
import no.nav.su.se.bakover.common.tid.periode.Periode

/**
 * En sortert liste med perioder som ikke overlapper.
 * Trenger ikke å være minste sammenhengende (tilstøtende perioder er slått sammen). Se [NonEmptySlåttSammenIkkeOverlappendePerioder] og [NonEmptyIkkeOverlappendePerioder].
 * Dette er en sterkere garanti enn [NonEmptyOverlappendePerioder], og kan brukes på steder hvor det er viktig at periodene ikke overlapper, f.eks. sortering.
 *
 * Periodene kan fremdeles ha hull.
 */
sealed interface IkkeOverlappendePerioder : Perioder {

    companion object {
        fun create(perioder: List<Periode>): IkkeOverlappendePerioder {
            if (perioder.isEmpty()) return EmptyPerioder
            return create(perioder.toNonEmptyList())
        }

        fun create(perioder: NonEmptyList<Periode>): NonEmptyIkkeOverlappendePerioder {
            return if (perioder.minsteAntallSammenhengendePerioder().perioder.size < perioder.size) {
                NonEmptyIkkeOverlappendePerioder.create(perioder.toNonEmptyList())
            } else {
                NonEmptySlåttSammenIkkeOverlappendePerioder.create(perioder.toNonEmptyList())
            }
        }
    }
}
