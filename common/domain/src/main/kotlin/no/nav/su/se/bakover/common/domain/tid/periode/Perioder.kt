package no.nav.su.se.bakover.common.domain.tid.periode

import no.nav.su.se.bakover.common.domain.extensions.toNonEmptyList
import no.nav.su.se.bakover.common.domain.tid.periode.NonEmptySlåttSammenIkkeOverlappendePerioder.Companion.nonEmptyMinsteAntallSammenhengendePerioder
import no.nav.su.se.bakover.common.tid.periode.Periode

/**
 * Generelt grensesnitt for en liste med perioder.
 * Periodene kan overlappe, det kan være hull og rekkefølgen kan være vilkårlig.
 */
sealed interface Perioder : List<Periode> {
    val perioder: List<Periode>
    // TODO jah: Flytt List<Periode>-funksjoner hit.

    /**
     * Finner minste antall sammenhengende perioder fra en liste med [Periode] ved å slå sammen elementer etter reglene
     * definert av [Periode.slåSammen].
     */
    fun List<Periode>.minsteAntallSammenhengendePerioder(): SlåttSammenIkkeOverlappendePerioder {
        if (this.isEmpty()) return EmptyPerioder
        return this.toNonEmptyList().nonEmptyMinsteAntallSammenhengendePerioder()
    }

    companion object {
        fun create(perioder: List<Periode>): Perioder {
            return when {
                perioder.isEmpty() -> EmptyPerioder
                else -> NonEmptyPerioder.create(perioder.toNonEmptyList())
            }
        }
    }
}
