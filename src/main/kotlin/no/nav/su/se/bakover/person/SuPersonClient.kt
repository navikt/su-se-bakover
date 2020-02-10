package no.nav.su.se.bakover.person

import com.github.kittinunf.fuel.httpGet
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders.Authorization
import io.ktor.http.HttpHeaders.XRequestId
import io.ktor.http.HttpStatusCode
import no.nav.su.se.bakover.Resultat
import no.nav.su.se.bakover.azure.TokenExchange
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.slf4j.MDC

internal interface PersonOppslag {
    fun person(ident: String, innloggetSaksbehandlerToken: String): Resultat
}

private const val suPersonIdentLabel = "ident"

internal class SuPersonClient(suPersonBaseUrl: String, private val suPersonClientId: String, private val tokenExchange: TokenExchange) :
        PersonOppslag {
    private val personResource = "$suPersonBaseUrl/person"

    override fun person(ident: String, innloggetSaksbehandlerToken: String): Resultat {
        val onBehalfOfToken = tokenExchange.onBehalfOFToken(innloggetSaksbehandlerToken, suPersonClientId)
        val (_, _, result) = personResource.httpGet(listOf(suPersonIdentLabel to ident))
                .header(Authorization, "Bearer $onBehalfOfToken")
                .header(XRequestId, MDC.get(XRequestId))
                .responseString()
        return result.fold(
                { Resultat.ok(it) },
                { error ->
                    val errorMessage = error.response.body().asString(ContentType.Application.Json.toString())
                    val statusCode = error.response.statusCode
                    logger.debug("Kall mot PDL feilet, statuskode: $statusCode, feilmelding: $errorMessage");
                    Resultat.feilMedMelding(HttpStatusCode.fromValue(statusCode), errorMessage)
                }
        )
    }

    companion object {
        val logger: Logger = LoggerFactory.getLogger(SuPersonClient::class.java)
    }
}

