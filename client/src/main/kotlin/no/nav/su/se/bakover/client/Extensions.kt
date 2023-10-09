@file:Suppress("ktlint:standard:function-naming")

package no.nav.su.se.bakover.client

import arrow.core.Either
import com.github.kittinunf.fuel.core.FuelError
import com.github.kittinunf.result.Result
import java.net.http.HttpRequest
import java.net.http.HttpResponse

fun <A : Any, B : FuelError> Either.Companion.fromResult(r: Result<A, B>): Either<B, A> =
    r.fold(
        { Either.Right(it) },
        { Either.Left(it) },
    )

fun <T> HttpResponse<T>.isSuccess(): Boolean = this.statusCode() in 200..299

/** Just delete this if Java adds this to its API */
fun HttpRequest.Builder.PATCH(bodyPublisher: HttpRequest.BodyPublisher): HttpRequest.Builder {
    return this.method("PATCH", bodyPublisher)
}
