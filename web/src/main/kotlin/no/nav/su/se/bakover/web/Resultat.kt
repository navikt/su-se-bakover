package no.nav.su.se.bakover.web

import io.ktor.application.ApplicationCall
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.response.respondText
import no.nav.su.se.bakover.client.ClientResponse

/* forstår seg på hvordan et resultat med en melding blir til en http-response */
internal class Resultat private constructor(
    private val httpCode: HttpStatusCode,
    private val json: String,
    private val contentType: ContentType
) {
    init {
        require(httpCode in HttpStatusCode.allStatusCodes) { "Unknown http status code:$httpCode" }
    }

    override fun equals(other: Any?) = other is Resultat && other.httpCode == this.httpCode && other.json == this.json
    override fun hashCode(): Int = 31 * httpCode.value + json.hashCode()
    suspend fun svar(call: ApplicationCall) =
        call.respondText(contentType = contentType, status = httpCode, text = json)

    companion object {
        fun message(httpCode: HttpStatusCode, message: String) = json(httpCode, """{"message": "$message"}""")
        fun json(httpCode: HttpStatusCode, json: String) =
            Resultat(httpCode, json, contentType = ContentType.Application.Json)

        fun from(clientResponse: ClientResponse) =
            json(HttpStatusCode.fromValue(clientResponse.httpStatus), clientResponse.content)
    }
}

internal fun HttpStatusCode.message(nonJsonMessage: String): Resultat = Resultat.message(this, nonJsonMessage)
internal suspend fun ApplicationCall.svar(resultat: Resultat) = resultat.svar(this)
