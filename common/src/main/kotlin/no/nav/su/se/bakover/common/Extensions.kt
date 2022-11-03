package no.nav.su.se.bakover.common

import arrow.core.NonEmptyList
import arrow.core.toNonEmptyListOrNull
import no.nav.su.se.bakover.common.periode.Periode
import java.lang.Double.max
import java.lang.Double.min
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

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

fun String.trimWhitespace(): String {
    return this.filterNot { it.isWhitespace() }
}

fun <T> List<T>.toNonEmptyList(): NonEmptyList<T> {
    return this.toNonEmptyListOrNull() ?: throw IllegalArgumentException("Kan ikke lage NonEmptyList fra en tom liste.")
}

inline fun Boolean.and(predicate: () -> Boolean): Boolean {
    return this && predicate()
}

fun ClosedRange<LocalDate>.toPeriode(): Periode {
    return Periode.create(
        fraOgMed = this.start,
        tilOgMed = this.endInclusive,
    )
}

fun LocalDate.førsteINesteMåned(): LocalDate {
    return this.plusMonths(1).startOfMonth()
}

fun LocalDate.sisteIForrigeMåned(): LocalDate {
    return this.minusMonths(1).endOfMonth()
}
