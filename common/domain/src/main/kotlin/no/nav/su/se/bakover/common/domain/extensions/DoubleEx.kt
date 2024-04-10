package no.nav.su.se.bakover.common.domain.extensions

fun Double.positiveOrZero(): Double = java.lang.Double.max(0.0, this)
fun Double.limitedUpwardsTo(limit: Double): Double = java.lang.Double.min(limit, this)
fun Double.roundToDecimals(decimals: Int): Double = this.toBigDecimal().roundToDecimals(decimals)
