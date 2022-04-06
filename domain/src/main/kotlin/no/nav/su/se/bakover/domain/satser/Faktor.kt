package no.nav.su.se.bakover.domain.satser

import java.math.BigDecimal

@JvmInline
value class Faktor(val value: Double) {
    init {
        assert(value > 0)
    }
    fun toBigDecimal(): BigDecimal = value.toBigDecimal()

    operator fun compareTo(other: Int): Int {
        return value.compareTo(other)
    }
}
