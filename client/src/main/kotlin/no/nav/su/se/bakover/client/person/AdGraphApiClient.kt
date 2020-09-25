package no.nav.su.se.bakover.client.person

import arrow.core.Either
import arrow.core.flatMap
import com.fasterxml.jackson.module.kotlin.readValue
import com.github.kittinunf.fuel.core.extensions.authentication
import com.github.kittinunf.fuel.httpGet
import no.nav.su.se.bakover.client.azure.OAuth
import no.nav.su.se.bakover.client.fromResult
import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.common.unsafeCatch
import org.slf4j.Logger
import org.slf4j.LoggerFactory

data class AdGraphResponse(
    val onPremisesSamAccountName: String?,
    val displayName: String,
    val givenName: String,
    val mail: String,
    val officeLocation: String,
    val surname: String,
    val userPrincipalName: String,
    val id: String,
    val jobTitle: String
)

interface AdGraphApiOppslag {
    fun hent(userToken: String): Either<String, AdGraphResponse>
}

class AdGraphApiClient(
    private val exchange: OAuth
) : AdGraphApiOppslag {
    private val log: Logger = LoggerFactory.getLogger(this::class.java)

    override fun hent(userToken: String): Either<String, AdGraphResponse> {
        val onBehalfOfToken = exchange.onBehalfOFToken(userToken, "https://graph.microsoft.com")
        val query =
            "onPremisesSamAccountName,displayName,givenName,mail,officeLocation,surname,userPrincipalName,id,jobTitle"
        val graphUrl = "https://graph.microsoft.com/v1.0/me"

        val (_, _, result) = graphUrl.httpGet(
            listOf(
                "\$select" to query
            )
        )
            .authentication()
            .bearer(onBehalfOfToken)
            .header("Accept", "application/json")
            .responseString()

        return Either.fromResult(result)
            .mapLeft { error ->
                val errorMessage = error.response.body().asString("application/json")
                val statusCode = error.response.statusCode
                log.info("Kall til Microsoft Graph API feilet med kode $statusCode og melding: $errorMessage")
                errorMessage
            }
            .flatMap { res ->
                Either.unsafeCatch {
                    objectMapper.readValue<AdGraphResponse>(res)
                }
                    .mapLeft {
                        log.info("Deserialisering av respons fra Microsoft Graph API feilet")
                        "Deserialisering av respons fra Microsoft Graph API feilet"
                    }
            }
    }
}
