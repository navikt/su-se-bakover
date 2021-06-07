package no.nav.su.se.bakover.web

import arrow.core.Either
import com.auth0.jwt.interfaces.Payload
import io.ktor.application.ApplicationCall
import io.ktor.auth.Principal
import io.ktor.auth.jwt.JWTCredential
import io.ktor.auth.jwt.JWTPrincipal
import io.ktor.features.callId
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.request.header
import io.ktor.request.receiveStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.nav.su.se.bakover.common.ApplicationConfig
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.log
import no.nav.su.se.bakover.common.sikkerLogg
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.web.features.suUserContext
import java.util.UUID

internal fun ApplicationCall.sikkerlogg(msg: String) {
    sikkerLogg.info("${suUserContext.navIdent} $msg")
}

/**
 * Logg til audit.nais (som går videre til ArcSight)
 * @see AuditLogger
 */
internal fun ApplicationCall.audit(berørtBruker: Fnr, action: AuditLogEvent.Action, behandlingId: UUID?) {
    AuditLogger.log(
        AuditLogEvent(
            suUserContext.navIdent,
            berørtBruker,
            action,
            behandlingId,
            this.callId
        )
    )
}

internal fun getGroupsFromJWT(applicationConfig: ApplicationConfig, principal: Principal?): List<String> =
    getGroupsFromJWT(applicationConfig, (principal as JWTPrincipal).payload)

internal fun getGroupsFromJWT(applicationConfig: ApplicationConfig, credential: JWTCredential): List<String> =
    getGroupsFromJWT(applicationConfig, credential.payload)

private fun getGroupsFromJWT(applicationConfig: ApplicationConfig, payload: Payload): List<String> =
    // Token som genereres lokalt (av navikt/oauth2-mock-server) vil ikke inneholde gruppene, så vi legger dem på her
    if (applicationConfig.runtimeEnvironment == ApplicationConfig.RuntimeEnvironment.Local) {
        applicationConfig.azure.groups.let {
            listOf(
                it.veileder,
                it.saksbehandler,
                it.attestant,
                it.drift
            )
        }
    } else {
        payload.getClaim("groups").asList(String::class.java)
    }

internal fun getNAVidentFromJwt(applicationConfig: ApplicationConfig, principal: Principal?): String =
    if (applicationConfig.runtimeEnvironment == ApplicationConfig.RuntimeEnvironment.Local) {
        "Z9999999"
    } else {
        (principal as JWTPrincipal).payload.getClaim("NAVident").asString()
    }

internal fun getNavnFromJwt(applicationConfig: ApplicationConfig, principal: Principal?): String =
    if (applicationConfig.runtimeEnvironment == ApplicationConfig.RuntimeEnvironment.Local) {
        "Ulrik Utvikler"
    } else {
        (principal as JWTPrincipal).payload.getClaim("name").asString()
    }

internal fun String.toUUID() =
    Either.catch { UUID.fromString(this@toUUID) }
        .mapLeft { "${this@toUUID} er ikke en gyldig UUID" }

internal fun ApplicationCall.lesUUID(param: String) =
    this.parameters[param]?.let {
        it.toUUID().mapLeft { "$param er ikke en gyldig UUID" }
    } ?: Either.Left("$param er ikke et parameter")

internal fun ApplicationCall.parameter(parameterName: String) =
    this.parameters[parameterName]?.let { Either.Right(it) } ?: Either.Left("$parameterName er ikke et parameter")

fun ApplicationCall.authHeader() = this.request.header(HttpHeaders.Authorization).toString()

internal suspend inline fun <reified T> deserialize(call: ApplicationCall): T =
    deserialize(call.receiveTextUTF8())

internal suspend inline fun ApplicationCall.receiveTextUTF8(): String {
    return withContext(Dispatchers.IO) {
        String(receiveStream().readBytes())
    }
}

internal suspend fun ApplicationCall.withSakId(ifRight: suspend (UUID) -> Unit) {
    this.lesUUID("sakId").fold(
        ifLeft = { this.svar(HttpStatusCode.BadRequest.message(it)) },
        ifRight = { ifRight(it) },
    )
}

internal suspend fun ApplicationCall.withRevurderingId(ifRight: suspend (UUID) -> Unit) {
    this.lesUUID("revurderingId").fold(
        ifLeft = { this.svar(HttpStatusCode.BadRequest.message(it)) },
        ifRight = { ifRight(it) },
    )
}

internal suspend fun ApplicationCall.withSøknadId(ifRight: suspend (UUID) -> Unit) {
    this.lesUUID("søknadId").fold(
        ifLeft = { this.svar(HttpStatusCode.BadRequest.message(it)) },
        ifRight = { ifRight(it) },
    )
}

internal suspend fun ApplicationCall.withBehandlingId(ifRight: suspend (UUID) -> Unit) {
    this.lesUUID("behandlingId").fold(
        ifLeft = { this.svar(HttpStatusCode.BadRequest.message(it)) },
        ifRight = { ifRight(it) },
    )
}

internal suspend inline fun <reified T> ApplicationCall.withBody(ifRight: (T) -> Unit) {
    Either.catch { deserialize<T>(this) }.fold(
        ifLeft = {
            log.error("Feil ved deserialisering", it)
            this.svar(HttpStatusCode.BadRequest.message("Ugyldig body"))
        },
        ifRight = { ifRight(it) },
    )
}
