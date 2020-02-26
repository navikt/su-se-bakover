package no.nav.su.se.bakover.person

import com.github.kittinunf.fuel.httpGet
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders.Authorization
import io.ktor.http.HttpHeaders.XRequestId
import io.ktor.http.HttpStatusCode
import io.ktor.http.HttpStatusCode.Companion.OK
import no.nav.su.se.bakover.Fødselsnummer
import no.nav.su.se.bakover.Resultat
import no.nav.su.se.bakover.azure.TokenExchange
import no.nav.su.se.bakover.json
import no.nav.su.se.bakover.tekst
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.slf4j.MDC

internal interface PersonOppslag {
    fun person(ident: Fødselsnummer, innloggetSaksbehandlerToken: String): Resultat
}

private const val suPersonIdentLabel = "ident"

internal class SuPersonClient(suPersonBaseUrl: String, private val suPersonClientId: String, private val tokenExchange: TokenExchange) :
        PersonOppslag {
    private val personResource = "$suPersonBaseUrl/person"

    override fun person(ident: Fødselsnummer, innloggetSaksbehandlerToken: String): Resultat {
        val onBehalfOfToken = tokenExchange.onBehalfOFToken(innloggetSaksbehandlerToken, suPersonClientId)
        val (_, _, result) = personResource.httpGet(listOf(suPersonIdentLabel to ident.toString()))
                .header(Authorization, "Bearer $onBehalfOfToken")
                .header(XRequestId, MDC.get(XRequestId))
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

    companion object {
        val logger: Logger = LoggerFactory.getLogger(SuPersonClient::class.java)
    }
}

