package no.nav.su.se.bakover.common

/**
 * 0-indeksert rekkefølge. Brukes for å sortere i stigende rekkefølge.
 */
@JvmInline
value class Rekkefølge(
    val value: Long,
) : Comparable<Rekkefølge> {

    init {
        require(value >= 0) { "Rekkefølge må være større enn eller lik 0" }
    }

    override fun compareTo(other: Rekkefølge): Int {
        return value.compareTo(other.value)
    }

    fun neste(): Rekkefølge {
        return Rekkefølge(value + 1)
    }

    /**
     * Hopper over de N neste.
     * @param hoppOver 0 gir neste, 1 gir neste neste, osv.
     */
    fun skip(hoppOver: Long): Rekkefølge {
        require(hoppOver >= 0) { "Kan ikke være negativt" }
        return Rekkefølge(value + hoppOver + 1)
    }

    companion object {

        fun start(): Rekkefølge {
            return Rekkefølge(0)
        }

        /**
         * Starter på start og over de N neste.
         * @param hoppOver 0 gir neste, 1 gir neste neste, osv.
         */
        fun skip(hoppOver: Long): Rekkefølge {
            require(hoppOver >= 0) { "Kan ikke være negativt" }
            return start().skip(hoppOver)
        }

        fun generator(): RekkefølgeGenerator {
            return RekkefølgeGenerator()
        }
    }
}

class RekkefølgeGenerator {
    private var nextValue: Rekkefølge = Rekkefølge.start()
    fun neste(): Rekkefølge {
        return nextValue.also { nextValue = nextValue.neste() }
    }
}
