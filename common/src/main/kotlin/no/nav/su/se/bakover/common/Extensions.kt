package no.nav.su.se.bakover.common

import arrow.core.Either
import kotlinx.coroutines.runBlocking

fun <A> Either.Companion.unsafeCatch(f: () -> A) =
    runBlocking {
        Either.catch { f() }
    }

// Lager vår egen her fordi Arrow sin filterMap bruker Option, som Arrow selv sier er deprecated (noe som feiler bygget vårt)
fun <A, B> List<A>.filterMap(predicate: Function1<A, B?>): List<B> =
    fold(emptyList()) { acc, a ->
        predicate(a).let {
            when (it) {
                null -> acc
                else -> acc.plus(it)
            }
        }
    }

fun Double.positiveOrZero() = when (this < 0) {
    true -> 0.0
    false -> this
}

fun Double.limitedUpwardsTo(limit: Double) = when (this > limit) {
    true -> limit
    false -> this
}
