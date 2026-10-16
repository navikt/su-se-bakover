package vilkår.flyktningsstatus.infrastructure.client

import arrow.core.Either
import arrow.core.right
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames
import no.nav.su.se.bakover.common.auth.AzureAd
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.infrastructure.token.JwtToken
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import vilkår.flyktnings.domain.UlfClient
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

class UlfClientStub : UlfClient {
    override fun hentEndringer() {
    }
}

class UlfHttpClient(
    // configs..
    private val hentBrukerToken: () -> JwtToken.BrukerToken = { JwtToken.BrukerToken.fraCoroutineContext() },
    private val azureAd: AzureAd,
) : UlfClient {

    companion object {
        const val KLIENT_ID = "TODO"
        const val URL = "https://apim.utv08.udiutv.no/UlfKonsument"
        const val URL_ENDRINGER = "$URL/personer/endringer?limit=100"
    }

    private val log: Logger = LoggerFactory.getLogger(this::class.java)

    private val client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .followRedirects(HttpClient.Redirect.NEVER)
        .build()

    private fun getToken(): TokenResponse = try {
        val naisMpUrl = "http://127.0.0.1:7164/api/v1/token"
        val requestBody = HttpRequest.BodyPublishers.ofString(
            """
            {"identity_provider":"maskinporten","target":"udi:ulfkonsument/endringer"}
            """.trimIndent(),
        )

        val getRequest = HttpRequest.newBuilder()
            .uri(URI.create(naisMpUrl))
            .header("Content-Type", "application/json")
            .POST(requestBody)
            .build()

        val respons = client.send(getRequest, HttpResponse.BodyHandlers.ofString())
        deserialize<TokenResponse>(respons.body())
    } catch (e: Exception) {
        log.error("Ulf feilet ved uthenting av token maskinporten", e)
        throw e
    }

    override fun hentEndringer() {
        val token = getToken()
        // val enddringer_flyktningsstatus= "$URL/personer/$id?requestedFields=JuridiskStatus_Flyktningstatus" TODO

        val getRequest = HttpRequest.newBuilder()
            .uri(URI.create(URL_ENDRINGER))
            .header("Accept", "application/json")
            .header("Authorization", "Bearer $token")
            // .header("Nav-Call-Id", correlationId.toString())
            .header("Content-Type", "application/json")
            // .POST(HttpRequest.BodyPublishers.ofString(requestBody))
            .GET()
            .build()

        Either.catch {
            client.send(getRequest, HttpResponse.BodyHandlers.ofString())
        }.mapLeft {
            log.error("Fikk en exception ${it.message} i henting av data fra Ulf. Se sikkerlogg.", it)
            // SkatteoppslagFeil.Nettverksfeil(it)
        }.map { response ->
            // handleResponse(response, getRequest, fnr, inntektsÅr, stadie)
            val statusCode: Int = response.statusCode()

            val resultat = when (statusCode) {
                200 -> {
                    val body: String = response.body()
                    log.info("Fikk en status response $statusCode fra Ulf, body=$body")
                }
                else -> log.error("Fikk en status response $statusCode fra Ulf, response=$response")
            }
            "".right()
        }
    }
}

@Serializable
@OptIn(ExperimentalSerializationApi::class)
internal data class TokenResponse(
    @JsonNames("access_token")
    val accessToken: String,
    @JsonNames("expires_in")
    val expiresInSeconds: Int,
)
