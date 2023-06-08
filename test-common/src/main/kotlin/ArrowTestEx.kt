package no.nav.su.se.bakover.test

import arrow.core.Either
import arrow.core.getOrElse
import io.kotest.assertions.fail
import org.checkerframework.checker.units.qual.C

fun <A, B> Either<A, B>.getOrFail(msg: String): B {
    return getOrElse { fail("Message: $msg, Error: $it") }
}

fun <A, B> Either<A, B>.getOrFail(): B {
    return getOrElse { fail("""$it""") }
}

inline fun <reified C> Either<Any?, Any?>.getOrFailAsType(): C {
    return getOrElse { fail("""$it""") }.let { it as C }
}
