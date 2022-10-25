package no.nav.su.se.bakover.common.application

import no.nav.su.se.bakover.common.periode.Periode

/**
 * [no.nav.su.se.bakover.domain.tidslinje.Tidslinje] bruker funksjonen [copy] med [CopyArgs.Tidslinje] for å justere
 * perioden for objekter som skal plasseres på tidslinjen.
 *
 * @see [CopyArgs.Tidslinje]
 */
interface KopierbarForTidslinje<Type> {
    fun copy(args: CopyArgs.Tidslinje): Type
}

/**
 * Kopier objektet og sett perioden til å være snittet av original og perioden angitt av [CopyArgs.Snitt]
 */
interface KopierbarForSnitt<Type> {
    fun copy(args: CopyArgs.Snitt): Type?
}

sealed class CopyArgs {
    sealed class Tidslinje : CopyArgs() {
        /**
         * Kopier aktuelt objekt og sett perioden til å være [periode].
         */
        data class NyPeriode(val periode: Periode) : Tidslinje()

        /**
         * Kopier aktuelt objekt som det er.
         */
        object Full : Tidslinje()

        /**
         * Spesielt argument for å kunne maskere elementer fra en [no.nav.su.se.bakover.domain.tidslinje.Tidslinje].
         * @throws IllegalArgumentException dersom [args] er av typen [Maskert] da dette vil føre til rekursiv loop.
         *
         * @see implementasjoner av [KopierbarForTidslinje.copy]
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
