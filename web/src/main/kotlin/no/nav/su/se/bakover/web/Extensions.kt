package no.nav.su.se.bakover.web

import arrow.core.Either
import io.ktor.application.Application
import io.ktor.application.ApplicationCall
import io.ktor.auth.authentication
import io.ktor.auth.jwt.JWTPrincipal
import io.ktor.config.ApplicationConfig
import io.ktor.http.HttpHeaders
import io.ktor.request.header
import io.ktor.request.receiveStream
import io.ktor.util.KtorExperimentalAPI
import kotlinx.coroutines.runBlocking
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.domain.Fnr
import org.slf4j.LoggerFactory
import java.util.UUID

@KtorExperimentalAPI
fun Application.fromEnvironment(path: String): String = environment.config.property(path).getString()

@KtorExperimentalAPI
internal fun ApplicationConfig.getProperty(key: String): String = property(key).getString()

internal fun ApplicationCall.audit(msg: String) {
    val payload = (this.authentication.principal as JWTPrincipal).payload
    LoggerFactory.getLogger("sikkerLogg").info("${payload.getClaim("oid").asString()} $msg")
}

internal fun String.toUUID() =
    runBlocking {
        Either.catch { UUID.fromString(this@toUUID) }
            .mapLeft { "${this@toUUID} er ikke en gyldig UUID" }
    }

internal fun ApplicationCall.lesUUID(param: String) =
    this.parameters[param]?.let {
        it.toUUID().mapLeft { "$param er ikke en gyldig UUID" }
    } ?: Either.Left("$param er ikke et parameter")

internal suspend fun ApplicationCall.lesFnr(param: String) =
    this.parameters[param]?.let {
        Either.catch { Fnr(it) }.mapLeft { "$param er ikke et gyldig fødselsnummer" }
    } ?: Either.Left("$param er ikke et parameter")

fun ApplicationCall.authHeader() = this.request.header(HttpHeaders.Authorization).toString()

internal suspend inline fun <reified T> deserialize(call: ApplicationCall): T =
    deserialize(call.receiveTextUTF8())

suspend inline fun ApplicationCall.receiveTextUTF8(): String = String(receiveStream().readBytes())
