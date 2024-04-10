package no.nav.su.se.bakover.common.domain.extensions

import java.math.BigDecimal
import java.math.RoundingMode

fun BigDecimal.roundToDecimals(decimals: Int): Double = this.setScale(decimals, RoundingMode.HALF_UP).toDouble()
fun BigDecimal.scaleTo4(): BigDecimal = this.setScale(4, RoundingMode.HALF_UP)

/**
 * Runder av til nærmeste heltall basert på Norges Banks avrundingsregler (HALF_UP).
 *
 * @throws ArithmeticException dersom vi mister informasjon i konverteringa
 */
fun BigDecimal.avrund(): Int = this.setScale(0, RoundingMode.HALF_UP).intValueExact()
