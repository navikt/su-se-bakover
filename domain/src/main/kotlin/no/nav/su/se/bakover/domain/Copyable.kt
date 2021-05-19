package no.nav.su.se.bakover.domain

import no.nav.su.se.bakover.common.periode.Periode

interface Copyable<Args, Type> {
    fun copy(args: Args): Type
}

sealed class CopyArgs {
    sealed class Tidslinje : CopyArgs() {
        data class NyPeriode(val periode: Periode) : Tidslinje()
        object Full : Tidslinje()
    }

    /**
     * Lager kopi hvor perioden settes til snittet av periodene.
     */
    data class Snitt(val periode: Periode) : CopyArgs() {
        fun snittFor(original: Periode): Periode? {
            return original.snitt(periode)
        }
    }
}
