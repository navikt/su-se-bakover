package no.nav.su.se.bakover.client.person

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.left
import arrow.core.right
import com.github.kittinunf.fuel.core.Request
import com.github.kittinunf.fuel.core.extensions.authentication
import com.github.kittinunf.fuel.httpGet
import no.nav.su.se.bakover.client.fromResult
import no.nav.su.se.bakover.common.auth.AzureAd
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.sikkerLogg
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import person.domain.IdentClient
import person.domain.KunneIkkeHenteNavnForNavIdent

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
    val value: List<MicrosoftGraphResponse>,
)

internal class MicrosoftGraphApiClient(
    private val exchange: AzureAd,
    private val baseUrl: String = "https://graph.microsoft.com/v1.0",
    private val graphApiAppId: String = "https://graph.microsoft.com",
    private val selectFields: String = "onPremisesSamAccountName,displayName,givenName,mail,officeLocation,surname,userPrincipalName,id,jobTitle",
    private val equalityCheckFilterField: String = "onPremisesSamAccountName",
) : IdentClient {
    private val log: Logger = LoggerFactory.getLogger(this::class.java)

    override fun hentNavnForNavIdent(navIdent: NavIdentBruker): Either<KunneIkkeHenteNavnForNavIdent, String> {
        return hentBrukerinformasjonForNavIdent(navIdent).map { it.displayName }
    }

    private fun hentBrukerinformasjonForNavIdent(navIdent: NavIdentBruker): Either<KunneIkkeHenteNavnForNavIdent, MicrosoftGraphResponse> {
        val token = exchange.getSystemToken(graphApiAppId)

        return doReq<ListOfMicrosoftGraphResponse>(
            "$baseUrl/users".httpGet(
                listOf(
                    "\$select" to selectFields,
                    "\$filter" to "$equalityCheckFilterField eq '$navIdent'",
                    "\$count" to "true",
                ),
            )
                .authentication()
                .bearer(token)
                .header("ConsistencyLevel", "eventual"),
        ).flatMap {
            if (it.value.size != 1) {
                log.error("Fant ingen eller flere brukere for navIdent $navIdent: ${it.value.size}. Se sikker logg dersom vi fant flere.")
                if (it.value.isNotEmpty()) {
                    sikkerLogg.error("Fant ingen eller flere brukere for navIdent $navIdent: ${it.value}")
                }
                KunneIkkeHenteNavnForNavIdent.FantIkkeBrukerForNavIdent.left()
            } else {
                it.value.first().right()
            }
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
                    deserialize<T>(res)
                }.mapLeft {
                    log.error("Deserialisering av respons fra Microsoft Graph API feilet: $it, request-parametre: ${req.parameters}, response-string: $res")
                    KunneIkkeHenteNavnForNavIdent.DeserialiseringAvResponsFeilet
                }
            }
    }
}
