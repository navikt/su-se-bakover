package no.nav.su.se.bakover.domain

import no.nav.su.se.bakover.common.periode.Periode

interface Copyable<Args, Type> {
    fun copy(args: Args): Type
}

sealed class CopyArgs {
    sealed class Tidslinje : CopyArgs() {
        data class NyPeriode(val periode: Periode) : Tidslinje()
        object Full : Tidslinje()

        /**
         * Spesielt argument for å kunne maskere elementer fra en [no.nav.su.se.bakover.domain.tidslinje.Tidslinje].
         * @throws IllegalArgumentException dersom [args] er av typen [Maskert] da dette vil føre til rekursiv loop.
         *
         * @see implementasjoner av [Copyable.copy]
         */
        data class Maskert(val args: Tidslinje) : Tidslinje() {
            init {
                require(args !is Maskert) { "Ugyldig argument for maskering. Kaster for å forhindre rekursiv loop." }
            }
        }
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
