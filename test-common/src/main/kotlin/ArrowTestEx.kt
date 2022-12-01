package no.nav.su.se.bakover.test

import arrow.core.Either
import arrow.core.getOrHandle
import io.kotest.assertions.fail

fun <A, B> Either<A, B>.getOrFail(msg: String): B {
    return getOrHandle { fail("Message: $msg, Error: $it") }
}

fun <A, B> Either<A, B>.getOrFail(): B {
    return getOrHandle { fail("""$it""") }
}
