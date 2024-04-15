package no.nav.su.se.bakover.common.domain.tid.periode

import arrow.core.NonEmptyList
import arrow.core.nonEmptyListOf
import no.nav.su.se.bakover.common.domain.extensions.toNonEmptyList
import no.nav.su.se.bakover.common.domain.tid.periode.EmptyPerioder.minsteAntallSammenhengendePerioder
import no.nav.su.se.bakover.common.tid.periode.Måned
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.common.tid.periode.harOverlappende
import no.nav.su.se.bakover.common.tid.periode.måneder

/**
 * En sortert liste med perioder som ikke overlapper og er minste sammenhengende (tilstøtende perioder er slått sammen).
 * Dette er en sterkere garanti enn [NonEmptyIkkeOverlappendePerioder], og kan brukes på steder hvor det er viktig at periodene ikke overlapper, f.eks. sortering.
 *
 * Periodene kan fremdeles ha hull.
 */
data class NonEmptySlåttSammenIkkeOverlappendePerioder private constructor(
    override val perioder: NonEmptyList<Periode>,
) : NonEmptyIkkeOverlappendePerioder(perioder), SlåttSammenIkkeOverlappendePerioder {

    companion object {
        fun create(vararg perioder: Periode): NonEmptySlåttSammenIkkeOverlappendePerioder {
            return create(listOf(*perioder).toNonEmptyList())
        }
        fun create(periode: Periode): NonEmptySlåttSammenIkkeOverlappendePerioder {
            return create(nonEmptyListOf(periode))
        }
        fun create(perioder: NonEmptyList<Periode>): NonEmptySlåttSammenIkkeOverlappendePerioder {
            require(!perioder.harOverlappende()) {
                "Periodene skal ikke overlappe, men var: $perioder"
            }
            require(perioder.minsteAntallSammenhengendePerioder().perioder.size == perioder.size) {
                "Tilstøtende perioder skal være slått sammen, men var: $perioder"
            }
            require(perioder.sortedBy { it.fraOgMed } == perioder) {
                "Periodene skal være sortert på fraOgMed, men var $perioder"
            }
            return NonEmptySlåttSammenIkkeOverlappendePerioder(perioder)
        }

        fun NonEmptyList<Periode>.nonEmptyMinsteAntallSammenhengendePerioder(): NonEmptySlåttSammenIkkeOverlappendePerioder {
            if (this.size == 1) return NonEmptySlåttSammenIkkeOverlappendePerioder(this)
            val result = this.måneder().fold<Måned, List<Periode>>(emptyList()) { acc, måned ->
                if (acc.isEmpty()) {
                    listOf(måned.tilPeriode())
                } else {
                    acc.last().slåSammen(måned.tilPeriode()).fold(
                        { acc + måned.tilPeriode() },
                        { acc.dropLast(1) + it },
                    )
                }
            }
            return NonEmptySlåttSammenIkkeOverlappendePerioder(result.toNonEmptyList())
        }
    }
    override fun toString() = "NonEmptySlåttSammenIkkeOverlappendePerioder(perioder=$perioder)"
}
