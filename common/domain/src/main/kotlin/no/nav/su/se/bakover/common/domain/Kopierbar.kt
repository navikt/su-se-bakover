package no.nav.su.se.bakover.common

import no.nav.su.se.bakover.common.tid.periode.Periode

/**
 * [no.nav.su.se.bakover.domain.tidslinje.Tidslinje] bruker funksjonen [copy] med [CopyArgs.Tidslinje] for å justere
 * perioden for objekter som skal plasseres på tidslinjen.
 *
 * @see [CopyArgs.Tidslinje]
 */
interface KopierbarForTidslinje<out Type> {
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
