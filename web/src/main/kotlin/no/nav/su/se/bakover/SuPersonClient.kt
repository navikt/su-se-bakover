package no.nav.su.se.bakover

import com.github.kittinunf.fuel.httpGet
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders.Authorization
import io.ktor.http.HttpHeaders.XCorrelationId
import io.ktor.http.HttpStatusCode
import io.ktor.http.HttpStatusCode.Companion.OK
import org.json.JSONObject
import org.slf4j.Logger
import org.slf4j.LoggerFactory

internal interface PersonOppslag {
    fun person(ident: Fødselsnummer): Resultat
    fun aktørId(ident: Fødselsnummer): String
}

private const val suPersonIdentLabel = "ident"

internal class SuPersonClient(suPersonBaseUrl: String, private val suPersonClientId: String, private val OAuth: OAuth) :
    PersonOppslag {
    private val personResource = "$suPersonBaseUrl/person"

    override fun person(ident: Fødselsnummer): Resultat {
        val onBehalfOfToken = OAuth.onBehalfOFToken(ContextHolder.getSecurityContext().token, suPersonClientId)
        val (_, _, result) = personResource.httpGet(listOf(suPersonIdentLabel to ident.toString()))
                .header(Authorization, "Bearer $onBehalfOfToken")
                .header(XCorrelationId, ContextHolder.getMdc(XCorrelationId))
                .responseString()
        return result.fold(
                { OK.json(it) },
                { error ->
                    val errorMessage = error.response.body().asString(ContentType.Application.Json.toString())
                    val statusCode = error.response.statusCode
                    logger.debug("Kall mot PDL feilet, statuskode: $statusCode, feilmelding: $errorMessage")
                    HttpStatusCode.fromValue(statusCode).tekst(errorMessage)
                }
        )
    }

    override fun aktørId(ident: Fødselsnummer): String {
        val onBehalfOfToken = OAuth.onBehalfOFToken(ContextHolder.getSecurityContext().token, suPersonClientId)
        val (_, _, result) = personResource.httpGet(listOf(suPersonIdentLabel to ident.toString()))
                .header(Authorization, "Bearer $onBehalfOfToken")
                .header(XCorrelationId, ContextHolder.getMdc(XCorrelationId))
                .responseString()
        return result.fold(
                { JSONObject(it).getString("aktoerId") },
                { error ->
                    val errorMessage = error.response.body().asString(ContentType.Application.Json.toString())
                    val statusCode = error.response.statusCode
                    throw RuntimeException("Kall mot PDL feilet, statuskode: $statusCode, feilmelding: $errorMessage")
                }
        )
    }

    companion object {
        val logger: Logger = LoggerFactory.getLogger(SuPersonClient::class.java)
    }
}

