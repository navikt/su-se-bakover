package no.nav.su.se.bakover.common.extensions

import arrow.core.Either
import arrow.core.NonEmptyList
import arrow.core.Tuple4
import arrow.core.toNonEmptyListOrNull
import no.nav.su.se.bakover.common.tid.YearRange
import no.nav.su.se.bakover.common.tid.periode.Periode
import java.lang.Double.max
import java.lang.Double.min
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.Year

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

fun <FIRST, SECOND, MAP_FIRST_TO> Pair<FIRST, SECOND>.mapFirst(f: (FIRST) -> MAP_FIRST_TO) =
    Pair(f(first), second)

fun <FIRST, SECOND, MAP_SECOND_TO> Pair<FIRST, SECOND>.mapSecond(f: (SECOND) -> MAP_SECOND_TO) =
    Pair(first, f(second))

fun <FIRST, SECOND, THIRD, FOURTH, MAP_SECOND_TO> Tuple4<FIRST, SECOND, THIRD, FOURTH>.mapSecond(
    f: (SECOND) -> MAP_SECOND_TO,
): Tuple4<FIRST, MAP_SECOND_TO, THIRD, FOURTH> {
    return Tuple4(first, f(second), third, fourth)
}

fun <FIRST, SECOND, THIRD, FOURTH, MAP_THIRD_TO> Tuple4<FIRST, SECOND, THIRD, FOURTH>.mapThird(
    f: (THIRD) -> MAP_THIRD_TO,
): Tuple4<FIRST, SECOND, MAP_THIRD_TO, FOURTH> {
    return Tuple4(first, second, f(third), fourth)
}

fun <FIRST, SECOND, THIRD, FOURTH, MAP_FOURTH_TO> Tuple4<FIRST, SECOND, THIRD, FOURTH>.mapFourth(
    f: (FOURTH) -> MAP_FOURTH_TO,
): Tuple4<FIRST, SECOND, THIRD, MAP_FOURTH_TO> {
    return Tuple4(first, second, third, f(fourth))
}

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

infix fun Year.erI(yearRange: YearRange): Boolean = yearRange.any { it == this }

fun <T : List<Any>, R> T.whenever(isEmpty: () -> R, isNotEmpty: (T) -> R): R {
    return if (this.isEmpty()) isEmpty() else isNotEmpty(this)
}

fun <T, R> List<T>.pickByCondition(targetList: Collection<R>, condition: (T, R) -> Boolean): List<T> {
    return this.filter { mainElement -> targetList.any { condition(mainElement, it) } }
}

fun <T, R> List<T>.mapOneIndexed(transform: (int: Int, T) -> R): List<R> {
    return this.mapIndexed { idx, el -> transform(idx + 1, el) }
}

fun <A, B> Pair<A?, B?>.isFirstNull(): Boolean = this.first == null
fun <A, B> Pair<A?, B?>.isSecondNull(): Boolean = this.second == null
fun <A, B> Pair<A?, B?>.isEitherNull(): Boolean = isFirstNull() || isSecondNull()

fun <A, B, C> Pair<A?, B?>.wheneverEitherIsNull(eitherIsNull: () -> C, eitherIsNotNull: (Pair<A, B>) -> C): C {
    return if (this.isEitherNull()) eitherIsNull() else eitherIsNotNull(Pair(this.first!!, this.second!!))
}

fun <A, B> List<Either<A, B>>.split(): Pair<List<A>, List<B>> {
    val left = mutableListOf<A>()
    val right = mutableListOf<B>()
    this.forEach {
        it.fold(
            { left.add(it) },
            { right.add(it) },
        )
    }
    return Pair(left, right)
}

fun <T> (() -> Boolean).whenever(isFalse: () -> T, isTrue: () -> T): T {
    return if (this()) isTrue() else isFalse()
}

fun <T> Boolean.whenever(isFalse: () -> T, isTrue: () -> T): T {
    return if (this) isTrue() else isFalse()
}
