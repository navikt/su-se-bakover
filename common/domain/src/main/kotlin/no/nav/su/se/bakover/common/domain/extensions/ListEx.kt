package no.nav.su.se.bakover.common.domain.extensions

import arrow.core.NonEmptyList
import arrow.core.toNonEmptyListOrNull

fun <T, R> List<T>.mapOneIndexed(transform: (int: Int, T) -> R): List<R> {
    return this.mapIndexed { idx, el -> transform(idx + 1, el) }
}

fun <T, R> List<T>.pickByCondition(targetList: Collection<R>, condition: (T, R) -> Boolean): List<T> {
    return this.filter { mainElement -> targetList.any { condition(mainElement, it) } }
}

fun <T> List<T>.toNonEmptyList(): NonEmptyList<T> {
    return this.toNonEmptyListOrNull() ?: throw IllegalArgumentException("Kan ikke lage NonEmptyList fra en tom liste.")
}
