package no.nav.su.se.bakover.client

import arrow.core.Either
import com.github.kittinunf.fuel.core.FuelError
import com.github.kittinunf.result.Result

fun <A : Any, B : FuelError> Either.Companion.fromResult(r: Result<A, B>) =
    r.fold(
        { Either.right(it) },
        { Either.left(it) }
    )
