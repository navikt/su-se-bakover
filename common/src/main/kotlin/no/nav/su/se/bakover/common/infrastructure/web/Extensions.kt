package no.nav.su.se.bakover.common.infrastructure.web

import arrow.core.Either
import com.auth0.jwt.interfaces.Payload
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.Principal
import io.ktor.server.auth.jwt.JWTCredential
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.plugins.callid.callId
import io.ktor.server.request.header
import io.ktor.server.request.receiveStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.nav.su.se.bakover.common.ApplicationConfig
import no.nav.su.se.bakover.common.Fnr
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.infrastructure.audit.AuditLogEvent
import no.nav.su.se.bakover.common.infrastructure.audit.AuditLogger
import no.nav.su.se.bakover.common.log
import no.nav.su.se.bakover.common.sikkerLogg
import java.util.UUID

fun ApplicationCall.sikkerlogg(msg: String) {
    sikkerLogg.info("${suUserContext.navIdent} $msg")
}

/**
 * Logg til audit.nais (som går videre til ArcSight)
 * @see AuditLogger
 */
fun ApplicationCall.audit(
    berørtBruker: Fnr,
    action: AuditLogEvent.Action,
    behandlingId: UUID?,
) {
    AuditLogger.log(
        AuditLogEvent(
            navIdent = suUserContext.navIdent,
            berørtBrukerId = berørtBruker,
            action = action,
            behandlingId = behandlingId,
            callId = this.callId,
        ),
    )
}

fun getGroupsFromJWT(applicationConfig: ApplicationConfig, principal: Principal?): List<String> =
    getGroupsFromJWT(applicationConfig, (principal as JWTPrincipal).payload)

fun getGroupsFromJWT(applicationConfig: ApplicationConfig, credential: JWTCredential): List<String> =
    getGroupsFromJWT(applicationConfig, credential.payload)

private fun getGroupsFromJWT(applicationConfig: ApplicationConfig, payload: Payload): List<String> =
    // Token som genereres lokalt (av navikt/oauth2-mock-server) vil ikke inneholde gruppene, så vi legger dem på her
    if (applicationConfig.runtimeEnvironment == ApplicationConfig.RuntimeEnvironment.Local) {
        applicationConfig.azure.groups.let {
            listOf(
                it.veileder,
                it.saksbehandler,
                it.attestant,
                it.drift,
            )
        }
    } else {
        payload.getClaim("groups").asList(String::class.java)
    }

fun getNAVidentFromJwt(applicationConfig: ApplicationConfig, principal: Principal?): String =
    if (applicationConfig.runtimeEnvironment == ApplicationConfig.RuntimeEnvironment.Local) {
        "Z9999999"
    } else {
        (principal as JWTPrincipal).payload.getClaim("NAVident").asString()
    }

fun getNavnFromJwt(applicationConfig: ApplicationConfig, principal: Principal?): String =
    if (applicationConfig.runtimeEnvironment == ApplicationConfig.RuntimeEnvironment.Local) {
        "Ulrik Utvikler"
    } else {
        (principal as JWTPrincipal).payload.getClaim("name").asString()
    }

fun String.toUUID() =
    Either.catch { UUID.fromString(this@toUUID) }
        .mapLeft { "${this@toUUID} er ikke en gyldig UUID" }

fun ApplicationCall.lesUUID(param: String) =
    this.parameters[param]?.let {
        it.toUUID().mapLeft { "$param er ikke en gyldig UUID" }
    } ?: Either.Left("$param er ikke et parameter")

fun ApplicationCall.parameter(parameterName: String) =
    this.parameters[parameterName]?.let { Either.Right(it) }
        ?: Either.Left(HttpStatusCode.BadRequest.errorJson("$parameterName er ikke et parameter", "parameter_mangler"))

fun ApplicationCall.authHeader() = this.request.header(HttpHeaders.Authorization).toString()

suspend inline fun <reified T> deserialize(call: ApplicationCall): T =
    deserialize(call.receiveTextUTF8())

suspend inline fun ApplicationCall.receiveTextUTF8(): String {
    return withContext(Dispatchers.IO) {
        String(receiveStream().readBytes())
    }
}

suspend fun ApplicationCall.withStringParam(parameterName: String, ifRight: suspend (String) -> Unit) {
    this.parameter(parameterName).fold(
        ifLeft = { this.svar(it) },
        ifRight = { ifRight(it) },
    )
}

suspend fun ApplicationCall.withSakId(ifRight: suspend (UUID) -> Unit) {
    this.lesUUID("sakId").fold(
        ifLeft = { this.svar(HttpStatusCode.BadRequest.errorJson(it, "sakId_mangler_eller_feil_format")) },
        ifRight = { ifRight(it) },
    )
}

suspend fun ApplicationCall.withRevurderingId(ifRight: suspend (UUID) -> Unit) {
    this.lesUUID("revurderingId").fold(
        ifLeft = { this.svar(HttpStatusCode.BadRequest.errorJson(it, "revurderingId_mangler_eller_feil_format")) },
        ifRight = { ifRight(it) },
    )
}

suspend fun ApplicationCall.withSøknadId(ifRight: suspend (UUID) -> Unit) {
    this.lesUUID("søknadId").fold(
        ifLeft = { this.svar(HttpStatusCode.BadRequest.errorJson(it, "søknadId_mangler_eller_feil_format")) },
        ifRight = { ifRight(it) },
    )
}

suspend fun ApplicationCall.withBehandlingId(ifRight: suspend (UUID) -> Unit) {
    this.lesUUID("behandlingId").fold(
        ifLeft = { this.svar(HttpStatusCode.BadRequest.errorJson(it, "behandlingId_mangler_eller_feil_format")) },
        ifRight = { ifRight(it) },
    )
}

suspend fun ApplicationCall.withVedtakId(ifRight: suspend (UUID) -> Unit) {
    this.lesUUID("vedtakId").fold(
        ifLeft = { this.svar(HttpStatusCode.BadRequest.errorJson(it, "vedtakId_mangler_eller_feil_format")) },
        ifRight = { ifRight(it) },
    )
}

suspend fun ApplicationCall.withKlageId(ifRight: suspend (UUID) -> Unit) {
    this.lesUUID("klageId").fold(
        ifLeft = { this.svar(HttpStatusCode.BadRequest.errorJson(it, "klageId_mangler_eller_feil_format")) },
        ifRight = { ifRight(it) },
    )
}

suspend inline fun <reified T> ApplicationCall.withBody(ifRight: (T) -> Unit) {
    Either.catch { this.receiveTextUTF8() }
        .tapLeft {
            log.error("Feil ved transformering av json-body til UTF-8 inn mot web-laget, se sikkerlogg for detaljer.")
            sikkerLogg.error("Feil ved transformering av json-body til UTF-8.", it)
            this.svar(Feilresponser.ugyldigBody)
        }.map { body ->
            Either.catch { deserialize<T>(body) }
                .tapLeft {
                    log.error("Feil ved deserialisering av json-body inn mot web-laget, se sikkerlogg for detaljer.")
                    sikkerLogg.error("Feil ved deserialisering av json-body: $body", it)
                    this.svar(Feilresponser.ugyldigBody)
                }.tap(ifRight)
        }
}
