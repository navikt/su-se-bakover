package no.nav.su.se.bakover.web

import io.ktor.application.ApplicationCall
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.response.respondText
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.web.Resultat.Companion.json

/* forstår seg på hvordan et resultat med en melding blir til en http-response */
internal data class Resultat private constructor(
    val httpCode: HttpStatusCode,
    private val json: String,
    private val contentType: ContentType,
) {
    init {
        require(httpCode in HttpStatusCode.allStatusCodes) { "Unknown http status code:$httpCode" }
    }

    suspend fun svar(call: ApplicationCall) =
        call.respondText(contentType = contentType, status = httpCode, text = json)

    companion object {
        fun json(httpCode: HttpStatusCode, json: String): Resultat =
            Resultat(httpCode, json, contentType = ContentType.Application.Json)
    }
}

internal fun HttpStatusCode.errorJson(message: String, code: String): Resultat {
    return json(this, serialize(ErrorJson(message, code)))
}

internal fun HttpStatusCode.errorJson(errors: List<ErrorJson>): Resultat {
    return json(this, serialize(errors))
}

internal suspend fun ApplicationCall.svar(resultat: Resultat) = resultat.svar(this)
