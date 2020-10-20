package no.nav.su.se.bakover.common

import arrow.core.Either
import kotlinx.coroutines.runBlocking

inline fun <A, B> Either<A, A>.foldBoth(f: (A) -> B) =
    fold(
        ifLeft = { f(it) },
        ifRight = { f(it) }
    )

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
