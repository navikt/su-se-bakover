package no.nav.su.se.bakover.common.domain.tid.periode

import arrow.core.NonEmptyList
import arrow.core.nonEmptyListOf
import no.nav.su.se.bakover.common.domain.extensions.toNonEmptyList
import no.nav.su.se.bakover.common.domain.tid.periode.EmptyPerioder.minsteAntallSammenhengendePerioder
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.common.tid.periode.harOverlappende

/**
 * En sortert liste med perioder som ikke overlapper, men som ikke nødvendigvis er slått sammen.
 * Dette er en sterkere garanti enn [NonEmptyOverlappendePerioder], og kan brukes på steder hvor det er viktig at periodene ikke overlapper, f.eks. sortering.
 *
 * Periodene kan fremdeles ha hull.
 */
open class NonEmptyIkkeOverlappendePerioder protected constructor(
    override val perioder: NonEmptyList<Periode>,
) : NonEmptyPerioder, IkkeOverlappendePerioder, List<Periode> by perioder {

    companion object {
        fun create(vararg perioder: Periode): NonEmptyIkkeOverlappendePerioder {
            return create(listOf(*perioder).toNonEmptyList())
        }
        fun create(periode: Periode): NonEmptyIkkeOverlappendePerioder {
            return create(nonEmptyListOf(periode))
        }

        /**
         * @param perioder Må være sortert på fraOgMed, ikke overlappe.
         *
         * @return Dersom periodene allerede er slått sammen; [NonEmptySlåttSammenIkkeOverlappendePerioder], ellers [NonEmptyIkkeOverlappendePerioder
         */
        fun create(perioder: NonEmptyList<Periode>): NonEmptyIkkeOverlappendePerioder {
            require(!perioder.harOverlappende()) {
                "Periodene skal ikke overlappe, men var: $perioder. Bruk heller NonEmptyOverlappendePerioder"
            }
            require(perioder.sortedBy { it.fraOgMed } == perioder) {
                "Periodene skal være sortert på fraOgMed, men var $perioder"
            }
            return if (perioder.minsteAntallSammenhengendePerioder().perioder.size < perioder.size) {
                NonEmptyIkkeOverlappendePerioder(perioder)
            } else {
                NonEmptySlåttSammenIkkeOverlappendePerioder.create(perioder)
            }
        }
    }
    override fun toString() = "NonEmptyIkkeOverlappendePerioder(perioder=$perioder)"
    override fun equals(other: Any?) = perioder == other
    override fun hashCode() = perioder.hashCode()
}
