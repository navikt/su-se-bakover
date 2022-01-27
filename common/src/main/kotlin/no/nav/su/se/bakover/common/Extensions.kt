package no.nav.su.se.bakover.common

import org.slf4j.MDC
import java.lang.Double.max
import java.lang.Double.min
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.UUID

fun Double.positiveOrZero() = max(0.0, this)
fun Double.limitedUpwardsTo(limit: Double) = min(limit, this)
fun Double.roundToDecimals(decimals: Int) = BigDecimal(this).setScale(decimals, RoundingMode.HALF_UP).toDouble()

fun <A, B> Pair<A, A>.mapBoth(f: (A) -> B): Pair<B, B> =
    Pair(f(first), f(second))

fun <FIRST, SECOND, MAP_SECOND_TO> Pair<FIRST, SECOND>.mapSecond(f: (SECOND) -> MAP_SECOND_TO) =
    Pair(first, f(second))

fun getOrCreateCorrelationId(): String {
    return MDC.get("X-Correlation-ID") ?: UUID.randomUUID().toString()
        .also { log.warn("Mangler X-Correlation-ID. Bruker random uuid $it") }
}
