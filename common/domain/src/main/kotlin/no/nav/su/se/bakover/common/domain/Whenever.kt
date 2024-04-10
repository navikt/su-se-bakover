package no.nav.su.se.bakover.common.domain

import no.nav.su.se.bakover.common.domain.extensions.isEitherNull

fun <T> (() -> Boolean).whenever(isFalse: () -> T, isTrue: () -> T): T {
    return if (this()) isTrue() else isFalse()
}

fun <T> Boolean.whenever(isFalse: () -> T, isTrue: () -> T): T {
    return if (this) isTrue() else isFalse()
}

fun <T : List<Any>, R> T.whenever(isEmpty: () -> R, isNotEmpty: (T) -> R): R {
    return if (this.isEmpty()) isEmpty() else isNotEmpty(this)
}

fun <A, B, C> Pair<A?, B?>.wheneverEitherIsNull(eitherIsNull: () -> C, eitherIsNotNull: (Pair<A, B>) -> C): C {
    return if (this.isEitherNull()) eitherIsNull() else eitherIsNotNull(Pair(this.first!!, this.second!!))
}
