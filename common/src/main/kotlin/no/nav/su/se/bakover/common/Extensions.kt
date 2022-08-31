package no.nav.su.se.bakover.common

import arrow.core.NonEmptyList
import org.slf4j.MDC
import java.lang.Double.max
import java.lang.Double.min
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.UUID

fun Double.positiveOrZero(): Double = max(0.0, this)
fun Double.limitedUpwardsTo(limit: Double): Double = min(limit, this)
fun Double.roundToDecimals(decimals: Int): Double = this.toBigDecimal().roundToDecimals(decimals)
fun BigDecimal.roundToDecimals(decimals: Int): Double = this.setScale(decimals, RoundingMode.HALF_UP).toDouble()
fun BigDecimal.scaleTo4(): BigDecimal = this.setScale(4, RoundingMode.HALF_UP)

/**
 * Runder av til nærmeste heltall basert på Norges Banks avrundingsregler (HALF_UP).
 *
 * @throws ArithmeticException dersom vi mister informasjon i konverteringa
 */
fun BigDecimal.avrund(): Int = this.setScale(0, RoundingMode.HALF_UP).intValueExact()

fun <A, B> Pair<A, A>.mapBoth(f: (A) -> B): Pair<B, B> =
    Pair(f(first), f(second))

fun <FIRST, SECOND, MAP_SECOND_TO> Pair<FIRST, SECOND>.mapSecond(f: (SECOND) -> MAP_SECOND_TO) =
    Pair(first, f(second))

fun getOrCreateCorrelationId(): String {
    return MDC.get("X-Correlation-ID") ?: UUID.randomUUID().toString()
        .also { log.warn("Mangler X-Correlation-ID. Bruker random uuid $it") }
}

fun String.trimWhitespace(): String {
    return this.filterNot { it.isWhitespace() }
}

fun <T> List<T>.nonEmpty(): NonEmptyList<T> {
    require(this.isNotEmpty()) { "Kan ikke lage NonEmptyList for en tom liste" }
    return NonEmptyList.fromListUnsafe(this)
}

inline fun Boolean.and(predicate: () -> Boolean): Boolean {
    return this && predicate()
}
