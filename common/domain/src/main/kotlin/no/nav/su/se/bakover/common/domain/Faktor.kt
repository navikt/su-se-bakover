package no.nav.su.se.bakover.common.domain

import java.math.BigDecimal

@JvmInline
value class Faktor(val value: Double) {
    init {
        require(value > 0)
    }
    fun toBigDecimal(): BigDecimal = value.toBigDecimal()

    operator fun compareTo(other: Int): Int {
        return value.compareTo(other)
    }
}
