package no.nav.su.se.bakover

import io.ktor.application.ApplicationCall
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import io.ktor.response.respond

/* forstår seg på hvordan et resultat med en melding blir til en http-response */
internal class Resultat private constructor(private val httpCode: HttpStatusCode, private val json: String) {
    override fun equals(other: Any?) = other is Resultat && other.httpCode == this.httpCode && other.json == this.json
    override fun hashCode(): Int = 31 * httpCode.value + json.hashCode()
    suspend fun svar(call: ApplicationCall) = call.respond(httpCode, json)

    fun fold(success: () -> Resultat, error: (Resultat) -> Resultat): Resultat = when {
        httpCode.isSuccess() -> success()
        else -> error(this)
    }

    companion object {
        fun resultatMedMelding(httpCode: HttpStatusCode, melding: String) = Resultat(httpCode, """{"message": "$melding"}""")
        fun resultatMedJson(httpCode: HttpStatusCode, json: String) = Resultat(httpCode, json)
    }
}

internal fun HttpStatusCode.json(json: String) = Resultat.resultatMedJson(this, json)
internal fun HttpStatusCode.tekst(renTekst: String) = Resultat.resultatMedMelding(this, renTekst)
internal suspend fun ApplicationCall.svar(resultat: Resultat) = resultat.svar(this)