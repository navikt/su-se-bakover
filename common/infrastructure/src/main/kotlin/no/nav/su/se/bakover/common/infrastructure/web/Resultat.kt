package no.nav.su.se.bakover.common.infrastructure.web

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respondText
import io.ktor.util.AttributeKey
import no.nav.su.se.bakover.common.infrastructure.web.Resultat.Companion.json
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.common.sikkerLogg
import org.slf4j.LoggerFactory

/* forstår seg på hvordan et resultat med en melding blir til en http-response */
data class Resultat(
    val httpCode: HttpStatusCode,
    val json: String,
    private val contentType: ContentType,
    private val errorCodes: List<String> = emptyList(),
) {
    private val log = LoggerFactory.getLogger(this::class.java)

    init {
        require(httpCode in HttpStatusCode.allStatusCodes) { "Unknown http status code:$httpCode" }
    }

    suspend fun svar(call: ApplicationCall) {
        val isCommitted = call.response.isCommitted
        val isSent = call.response.isSent
        if (isCommitted || isSent) {
            log.error(
                "Ktor-response already {committed=$isCommitted, sent=$isSent}. Ignored response: See sikkerLogg for more details.",
                IllegalStateException("Genererer en stacktrace for enklere debugging."),
            )
            sikkerLogg.error("Ktor-response already {committed=$isCommitted, sent=$isSent}. Ignored response: contentType=$contentType, httpCode=$httpCode, json=$json. See main log for stacktrace.")
        } else {
            call.attributes.put(ERROR_CODES_ATTRIBUTE_KEY, errorCodes)
            call.respondText(contentType = contentType, status = httpCode, text = json)
        }
    }

    companion object {
        val ERROR_CODES_ATTRIBUTE_KEY = AttributeKey<List<String>>("response_error_codes")

        fun json(httpCode: HttpStatusCode, json: String, errorCodes: List<String> = emptyList()): Resultat =
            Resultat(httpCode, json, contentType = ContentType.Application.Json, errorCodes = errorCodes)

        fun okJson() = json(HttpStatusCode.OK, """{"status": "OK"}""")
        fun accepted() = json(HttpStatusCode.Accepted, """{"status": "Accepted"}""")
    }
}

fun HttpStatusCode.errorJson(message: String, code: String): Resultat {
    return json(this, serialize(ErrorJson(message, code)), errorCodes = listOf(code))
}

fun HttpStatusCode.errorJson(errors: List<ErrorJson>): Resultat {
    return json(this, serialize(errors), errorCodes = errors.map { it.code })
}

suspend fun ApplicationCall.svar(resultat: Resultat) = resultat.svar(this)
