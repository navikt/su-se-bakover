package no.nav.su.se.bakover.client.person

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.left
import arrow.core.right
import com.fasterxml.jackson.module.kotlin.readValue
import com.github.kittinunf.fuel.core.Request
import com.github.kittinunf.fuel.core.extensions.authentication
import com.github.kittinunf.fuel.httpGet
import no.nav.su.se.bakover.client.azure.OAuth
import no.nav.su.se.bakover.client.fromResult
import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.common.unsafeCatch
import no.nav.su.se.bakover.domain.NavIdentBruker
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

data class ListOfMicrosoftGraphResponse(
    val value: List<MicrosoftGraphResponse>
)

interface MicrosoftGraphApiOppslag {
    fun hentBrukerinformasjon(userToken: String): Either<MicrosoftGraphApiOppslagFeil, MicrosoftGraphResponse>
    fun hentBrukerinformasjonForNavIdent(navIdent: NavIdentBruker): Either<MicrosoftGraphApiOppslagFeil, MicrosoftGraphResponse>
}

sealed class MicrosoftGraphApiOppslagFeil {
    object FeilVedHentingAvOnBehalfOfToken : MicrosoftGraphApiOppslagFeil()
    object KallTilMicrosoftGraphApiFeilet : MicrosoftGraphApiOppslagFeil()
    object DeserialiseringAvResponsFeilet : MicrosoftGraphApiOppslagFeil()
    object FantIkkeBrukerForNavIdent : MicrosoftGraphApiOppslagFeil()
}

class MicrosoftGraphApiClient(
    private val exchange: OAuth
) : MicrosoftGraphApiOppslag {
    private val log: Logger = LoggerFactory.getLogger(this::class.java)

    private val graphApiAppId = "https://graph.microsoft.com"
    private val userFields =
        "onPremisesSamAccountName,displayName,givenName,mail,officeLocation,surname,userPrincipalName,id,jobTitle"
    private val baseUrl = "https://graph.microsoft.com/v1.0"

    override fun hentBrukerinformasjon(userToken: String): Either<MicrosoftGraphApiOppslagFeil, MicrosoftGraphResponse> {
        val onBehalfOfToken = Either.unsafeCatch {
            exchange.onBehalfOfToken(userToken, graphApiAppId)
        }.let {
            when (it) {
                is Either.Left -> return MicrosoftGraphApiOppslagFeil.FeilVedHentingAvOnBehalfOfToken.left()
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

    override fun hentBrukerinformasjonForNavIdent(navIdent: NavIdentBruker): Either<MicrosoftGraphApiOppslagFeil, MicrosoftGraphResponse> {
        val token = exchange.getSystemToken(graphApiAppId)

        return doReq<ListOfMicrosoftGraphResponse>(
            "$baseUrl/users".httpGet(
                listOf(
                    "\$select" to userFields,
                    "\$filter" to "mailNickname eq '$navIdent'"
                )
            )
                .authentication()
                .bearer(token)
        ).flatMap {
            if (it.value.size != 1) MicrosoftGraphApiOppslagFeil.FantIkkeBrukerForNavIdent.left()
            else it.value.first().right()
        }
    }

    private inline fun <reified T> doReq(req: Request): Either<MicrosoftGraphApiOppslagFeil, T> {
        val (_, _, result) = req
            .header("Accept", "application/json")
            .responseString()

        return Either.fromResult(result)
            .mapLeft { error ->
                val errorMessage = error.response.body().asString("application/json")
                val statusCode = error.response.statusCode
                log.info("Kall til Microsoft Graph API feilet med kode $statusCode og melding: $errorMessage")
                MicrosoftGraphApiOppslagFeil.KallTilMicrosoftGraphApiFeilet
            }
            .flatMap { res ->
                Either.unsafeCatch {
                    objectMapper.readValue<T>(res)
                }.mapLeft {
                    log.info("Deserialisering av respons fra Microsoft Graph API feilet: $it")
                    MicrosoftGraphApiOppslagFeil.DeserialiseringAvResponsFeilet
                }
            }
    }
}
