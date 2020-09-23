package no.nav.su.se.bakover.common

import arrow.core.Either
import kotlinx.coroutines.runBlocking

fun <A> Either.Companion.unsafeCatch(f: () -> A) =
    runBlocking {
        Either.catch { f() }
    }
