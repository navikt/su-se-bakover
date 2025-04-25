package no.nav.su.se.bakover.common.domain.tid.periode

import arrow.core.NonEmptyList
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.common.tid.periode.harOverlappende

/**
 * I noen tilfeller operer vi på lister med perioder som kan ha overlapp, f.eks. i tidslinje sammenhenger.
 * I disse tilfellene er det ofte ikke tilstrekkelig å sortere periodene annet enn for visning. eks.:
 * - jan 21 - mars 21
 * - jan 21 - des 21
 * - feb 21 - april 21
 * - mars 21 - april 21
 *
 * TODO jah: Vurder om vi bør kreve at tilstøtende perioder er slått sammen og at de er sortert fra fraOgMed, deretter tilOgMed.
 */
data class NonEmptyOverlappendePerioder private constructor(
    override val perioder: NonEmptyList<Periode>,
) : NonEmptyPerioder,
    List<Periode> by perioder {

    companion object {
        fun create(perioder: NonEmptyList<Periode>): NonEmptyOverlappendePerioder {
            require(perioder.size > 1) {
                "NonEmptyOverlappendePerioder krever minst to perioder, men var: $perioder"
            }
            require(perioder.harOverlappende()) {
                "NonEmptyOverlappendePerioder krever at minst to perioder overlapper, men var: $perioder"
            }
            return NonEmptyOverlappendePerioder(perioder)
        }
    }
    override fun toString() = "NonEmptyOverlappendePerioder(perioder=$perioder)"
    override fun equals(other: Any?) = perioder == other
    override fun hashCode() = perioder.hashCode()
}
