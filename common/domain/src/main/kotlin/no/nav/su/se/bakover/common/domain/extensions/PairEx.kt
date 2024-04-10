package no.nav.su.se.bakover.common.domain.extensions

import arrow.core.Tuple4

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

fun <A, B> Pair<A?, B?>.isFirstNull(): Boolean = this.first == null
fun <A, B> Pair<A?, B?>.isSecondNull(): Boolean = this.second == null
fun <A, B> Pair<A?, B?>.isEitherNull(): Boolean = isFirstNull() || isSecondNull()
