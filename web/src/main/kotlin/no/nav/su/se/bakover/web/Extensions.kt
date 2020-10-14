package no.nav.su.se.bakover.web

import arrow.core.Either
import io.ktor.application.ApplicationCall
import io.ktor.auth.Principal
import io.ktor.auth.jwt.JWTPrincipal
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.request.header
import io.ktor.request.receiveStream
import kotlinx.coroutines.runBlocking
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.sikkerLogg
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.web.features.suUserContext
import java.util.UUID

internal fun ApplicationCall.audit(msg: String) {
    sikkerLogg.info("${suUserContext.getNAVIdent()} $msg")
}

internal fun getGroupsFromJWT(principal: Principal?): List<String> =
    (principal as JWTPrincipal).payload.getClaim("groups").asList(String::class.java)

internal fun String.toUUID() =
    runBlocking {
        Either.catch { UUID.fromString(this@toUUID) }
            .mapLeft { "${this@toUUID} er ikke en gyldig UUID" }
    }

internal fun String.toUUID30() =
    runBlocking {
        Either.catch { UUID30.fromString(this@toUUID30) }
            .mapLeft { "${this@toUUID30} er ikke en gyldig UUID" }
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

suspend fun ApplicationCall.withSakId(ifRight: suspend (UUID) -> Unit) {
    this.lesUUID("sakId").fold(
        ifLeft = { this.svar(HttpStatusCode.BadRequest.message(it)) },
        ifRight = { ifRight(it) }
    )
}

suspend fun ApplicationCall.withBehandlingId(ifRight: suspend (UUID) -> Unit) {
    this.lesUUID("behandlingId").fold(
        ifLeft = { this.svar(HttpStatusCode.BadRequest.message(it)) },
        ifRight = { ifRight(it) }
    )
}
