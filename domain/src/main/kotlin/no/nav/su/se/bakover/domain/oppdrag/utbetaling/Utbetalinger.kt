package no.nav.su.se.bakover.domain.oppdrag.utbetaling

import no.nav.su.se.bakover.domain.oppdrag.Utbetaling

/**
 * En wrapperklasse for utbetalinger. Kan legge inn hjelpefunksjoner og init-validering.
 * Krav:
 * - Alle utbetalinger må ha et unikt opprettet-tidspunkt.
 */
data class Utbetalinger(
    // TODO jah: Vurder om denne kan være kun oversendte utbetalinger.
    val utbetalinger: List<Utbetaling> = emptyList(),
) : List<Utbetaling> by utbetalinger {

    constructor(vararg utbetalinger: Utbetaling) : this(utbetalinger.toList())
    constructor(vararg utbetalinger: List<Utbetaling>) : this(utbetalinger.toList().flatten())

    init {
        this.map { it.opprettet }.let {
            require(it.distinct().size == it.size) { "Utbetalinger må ha unike opprettet-tidspunkt, men var $it" }
        }
    }

    infix operator fun plus(utbetaling: Utbetaling): Utbetalinger {
        return Utbetalinger(utbetalinger + utbetaling)
    }

    inline fun filterNot(predicate: (Utbetaling) -> Boolean): Utbetalinger {
        return Utbetalinger(utbetalinger.filterNot(predicate))
    }
}

infix operator fun List<Utbetaling>.plus(utbetaling: Utbetaling): Utbetalinger {
    val result = ArrayList<Utbetaling>(size + 1)
    result.addAll(this)
    result.add(utbetaling)
    return Utbetalinger(result)
}

infix operator fun List<Utbetaling>.plus(utbetalinger: List<Utbetaling>): Utbetalinger {
    val result = ArrayList<Utbetaling>(size + utbetalinger.size)
    result.addAll(this)
    result.addAll(utbetalinger)
    return Utbetalinger(result)
}

infix operator fun Utbetaling.plus(utbetaling: Utbetaling): Utbetalinger {
    val result = ArrayList<Utbetaling>(2)
    result.add(this)
    result.add(utbetaling)
    return Utbetalinger(result)
}

infix operator fun Utbetaling.plus(utbetalinger: List<Utbetaling>): Utbetalinger {
    val result = ArrayList<Utbetaling>(1 + utbetalinger.size)
    result.add(this)
    result.addAll(utbetalinger)
    return Utbetalinger(result)
}
