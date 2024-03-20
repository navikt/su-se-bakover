package no.nav.su.se.bakover.common.domain

import java.math.BigDecimal

fun BigDecimal.absoluttDiff(other: BigDecimal): BigDecimal {
    require(this > BigDecimal.ZERO && other > BigDecimal.ZERO)
    return if (this > other) {
        this.divide(other)
    } else {
        other.divide(this)
    }
}
