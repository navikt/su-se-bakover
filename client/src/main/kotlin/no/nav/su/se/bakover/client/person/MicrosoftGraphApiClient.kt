package no.nav.su.se.bakover.client.person

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.getOrHandle
import arrow.core.left
import arrow.core.right
import com.fasterxml.jackson.module.kotlin.readValue
import com.github.kittinunf.fuel.core.Request
import com.github.kittinunf.fuel.core.extensions.authentication
import com.github.kittinunf.fuel.httpGet
import no.nav.su.se.bakover.client.azure.OAuth
import no.nav.su.se.bakover.client.fromResult
import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.person.IdentClient
import no.nav.su.se.bakover.domain.person.KunneIkkeHenteNavnForNavIdent
import org.slf4j.Logger
import org.slf4j.LoggerFactory

private data class MicrosoftGraphResponse(
    val displayName: String,
    val givenName: String,
    val mail: String,
    val officeLocation: String,
    val surname: String,
    val userPrincipalName: String,
    val id: String,
    val jobTitle: String,
)

private data class ListOfMicrosoftGraphResponse(
    val value: List<MicrosoftGraphResponse>
)

internal class MicrosoftGraphApiClient(
    private val exchange: OAuth,
) : IdentClient {
    private val log: Logger = LoggerFactory.getLogger(this::class.java)

    private val graphApiAppId = "https://graph.microsoft.com"
    private val userFields =
        "onPremisesSamAccountName,displayName,givenName,mail,officeLocation,surname,userPrincipalName,id,jobTitle"
    private val baseUrl = "https://graph.microsoft.com/v1.0"

    override fun hentNavnForNavIdent(navIdent: NavIdentBruker): Either<KunneIkkeHenteNavnForNavIdent, String> {
        return hentBrukerinformasjonForNavIdent(navIdent).map { it.displayName }
    }

    private fun hentBrukerinformasjon(userToken: String): Either<KunneIkkeHenteNavnForNavIdent, MicrosoftGraphResponse> {
        val onBehalfOfToken = Either.catch {
            exchange.onBehalfOfToken(userToken, graphApiAppId)
        }.getOrHandle {
            return KunneIkkeHenteNavnForNavIdent.FeilVedHentingAvOnBehalfOfToken.left()
        }

        return doReq(
            "$baseUrl/me".httpGet(
                listOf(
                    "\$select" to userFields,
                ),
            )
                .authentication()
                .bearer(onBehalfOfToken),
        )
    }

    private fun hentBrukerinformasjonForNavIdent(navIdent: NavIdentBruker): Either<KunneIkkeHenteNavnForNavIdent, MicrosoftGraphResponse> {
        val token = exchange.getSystemToken(graphApiAppId)

        return doReq<ListOfMicrosoftGraphResponse>(
            "$baseUrl/users".httpGet(
                listOf(
                    "\$select" to userFields,
                    "\$filter" to "mailNickname eq '$navIdent'",
                ),
            )
                .authentication()
                .bearer(token),
        ).flatMap {
            if (it.value.size != 1) KunneIkkeHenteNavnForNavIdent.FantIkkeBrukerForNavIdent.left()
            else it.value.first().right()
        }
    }

    private inline fun <reified T> doReq(req: Request): Either<KunneIkkeHenteNavnForNavIdent, T> {
        val (_, _, result) = req
            .header("Accept", "application/json")
            .responseString()

        return Either.fromResult(result)
            .mapLeft { error ->
                val errorMessage = error.response.body().asString("application/json")
                val statusCode = error.response.statusCode
                log.error("Kall til Microsoft Graph API feilet med kode $statusCode, melding: $errorMessage, request-parametre: ${req.parameters}")
                KunneIkkeHenteNavnForNavIdent.KallTilMicrosoftGraphApiFeilet
            }
            .flatMap { res ->
                Either.catch {
                    objectMapper.readValue<T>(res)
                }.mapLeft {
                    log.error("Deserialisering av respons fra Microsoft Graph API feilet: $it, request-parametre: ${req.parameters}, response-string: $res")
                    KunneIkkeHenteNavnForNavIdent.DeserialiseringAvResponsFeilet
                }
            }
    }
}
