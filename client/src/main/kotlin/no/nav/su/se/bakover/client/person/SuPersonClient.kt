package no.nav.su.se.bakover.client.person

import com.github.kittinunf.fuel.httpGet
import no.nav.su.se.bakover.client.ClientResponse
import no.nav.su.se.bakover.client.azure.OAuth
import no.nav.su.se.bakover.domain.Fnr
import org.json.JSONObject
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.slf4j.MDC

private const val suPersonIdentLabel = "ident"

internal class SuPersonClient(
    suPersonBaseUrl: String,
    private val suPersonClientId: String,
    private val OAuth: OAuth
) :
    PersonOppslag {
    private val personResource = "$suPersonBaseUrl/person"

    override fun person(ident: Fnr): ClientResponse {
        val onBehalfOfToken = OAuth.onBehalfOFToken(MDC.get("Authorization"), suPersonClientId)
        val (_, response, result) = personResource.httpGet(listOf(suPersonIdentLabel to ident.toString()))
            .header("Authorization", "Bearer $onBehalfOfToken")
            .header("X-Correlation-ID", MDC.get("X-Correlation-ID"))
            .responseString()
        return result.fold(
            { ClientResponse(response.statusCode, it) },
            { error ->
                val errorMessage = error.response.body().asString("application/json")
                val statusCode = error.response.statusCode
                logger.debug("Kall mot PDL feilet, statuskode: $statusCode, feilmelding: $errorMessage")
                ClientResponse(response.statusCode, errorMessage)
            }
        )
    }

    override fun aktørId(ident: Fnr): String {
        val onBehalfOfToken = OAuth.onBehalfOFToken(MDC.get("Authorization"), suPersonClientId)
        val (_, _, result) = personResource.httpGet(listOf(suPersonIdentLabel to ident.toString()))
            .header("Authorization", "Bearer $onBehalfOfToken")
            .header("X-Correlation-ID", MDC.get("X-Correlation-ID"))
            .responseString()
        return result.fold(
            { JSONObject(it).getString("aktorId") },
            { error ->
                val errorMessage = error.response.body().asString("application/json")
                val statusCode = error.response.statusCode
                throw RuntimeException("Kall mot PDL feilet, statuskode: $statusCode, feilmelding: $errorMessage")
            }
        )
    }

    companion object {
        val logger: Logger = LoggerFactory.getLogger(SuPersonClient::class.java)
    }
}
