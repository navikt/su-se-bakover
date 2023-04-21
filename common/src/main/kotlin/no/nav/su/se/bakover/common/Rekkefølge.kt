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

        val FØRSTE = Rekkefølge.start()
        val ANDRE = Rekkefølge.skip(0)
        val TREDJE = Rekkefølge.skip(1)
        val FJERDE = Rekkefølge.skip(2)
        val FEMTE = Rekkefølge.skip(3)
    }
}

class RekkefølgeGenerator {
    private var nextValue: Rekkefølge = Rekkefølge.start()
    fun neste(): Rekkefølge {
        return nextValue.also { nextValue = nextValue.neste() }
    }
}
