package no.nav.su.se.bakover.client.person

import arrow.core.Either
import arrow.core.flatMap
import com.fasterxml.jackson.module.kotlin.readValue
import com.github.kittinunf.fuel.core.Request
import com.github.kittinunf.fuel.core.extensions.authentication
import com.github.kittinunf.fuel.httpGet
import no.nav.su.se.bakover.client.azure.OAuth
import no.nav.su.se.bakover.client.fromResult
import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.common.unsafeCatch
import org.slf4j.Logger
import org.slf4j.LoggerFactory

data class MicrosoftGraphResponse(
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

interface MicrosoftGraphApiOppslag {
    fun hentBrukerinformasjon(userToken: String): Either<String, MicrosoftGraphResponse>
    fun hentBrukerinformasjonForNavIdent(navIdent: String): Either<String, MicrosoftGraphResponse>
}

class MicrosoftGraphApiClient(
    private val exchange: OAuth
) : MicrosoftGraphApiOppslag {
    private val log: Logger = LoggerFactory.getLogger(this::class.java)

    private val graphApiAppId = "https://graph.microsoft.com"
    private val userFields = "onPremisesSamAccountName,displayName,givenName,mail,officeLocation,surname,userPrincipalName,id,jobTitle"
    private val baseUrl = "https://graph.microsoft.com/v1.0"

    override fun hentBrukerinformasjon(userToken: String): Either<String, MicrosoftGraphResponse> {
        val onBehalfOfToken = Either.unsafeCatch {
            exchange.onBehalfOFToken(userToken, graphApiAppId)
        }.let {
            when (it) {
                is Either.Left -> return Either.left(it.a.message ?: "Feil ved henting av onBehalfOfToken")
                is Either.Right -> it.b
            }
        }

        return doReq(
            "$baseUrl/me".httpGet(
                listOf(
                    "\$select" to userFields
                )
            )
                .authentication()
                .bearer(onBehalfOfToken)
        )
    }

    override fun hentBrukerinformasjonForNavIdent(navIdent: String): Either<String, MicrosoftGraphResponse> {
        val token = exchange.getSystemToken(graphApiAppId)

        return doReq(
            "$baseUrl/users".httpGet(
                listOf(
                    "\$select" to userFields,
                    "\$filter" to "mailNickname eq '$navIdent'"
                )
            )
                .authentication()
                .bearer(token)
        )
    }

    private fun doReq(req: Request): Either<String, MicrosoftGraphResponse> {
        val (_, _, result) = req
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
                    objectMapper.readValue<MicrosoftGraphResponse>(res)
                }
                    .mapLeft {
                        "Deserialisering av respons fra Microsoft Graph API feilet".also {
                            log.info(it)
                        }
                    }
            }
    }
}
