package vilkår.flyktningsstatus.infrastructure.client

import no.nav.su.se.bakover.common.auth.AzureAd
import no.nav.su.se.bakover.common.deserializeMap
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

// TODO flytte
class UlfHttpClient(
    // configs..
    private val hentBrukerToken: () -> JwtToken.BrukerToken = { JwtToken.BrukerToken.fraCoroutineContext() },
    private val azureAd: AzureAd,
) : UlfClient {

    companion object {
        const val KLIENT_ID = "TODO"
        const val URL = "https://apim.utv08.udiutv.no/UlfKonsument"
        const val URL_ENDRINGER = "$URL/personer/endringer?offset=1&limit=100"
        const val URL_PING = "$URL/ping/ping"

        const val SCOPE_ENDRINGER = "udi:ulfkonsument/endringer"
        const val SCOPE_FLYKTNINGSTATUS = "udi:ulfkonsument/flyktningstatus"
    }
    private val log: Logger = LoggerFactory.getLogger(this::class.java)

    private val client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .followRedirects(HttpClient.Redirect.NEVER)
        .build()

    private fun getToken() = try {
        val naisMpUrl = "http://127.0.0.1:7164/api/v1/token"
        val requestBody = HttpRequest.BodyPublishers.ofString(
            """
            {"identity_provider":"maskinporten","target":"$SCOPE_ENDRINGER"}
            """.trimIndent(),
        )

        val getRequest = HttpRequest.newBuilder()
            .uri(URI.create(naisMpUrl))
            .header("Content-Type", "application/json")
            .POST(requestBody)
            .build()

        val httpRespons = client.send(getRequest, HttpResponse.BodyHandlers.ofString())
        if (httpRespons.isSuccess()) {
            val body: String = httpRespons.body()
            log.info("UlfKonsument - Maskinporten OK responseBody: $body")
            val map = deserializeMap<String, Any>(body)
            map["access_token"] as String
        } else {
            val statusCode = httpRespons.statusCode()
            val body: String = httpRespons.body()
            log.error("UlfKonsument - feilet ved uthenting av token maskinporten. stausCode=$statusCode, body=$body")
            throw IllegalStateException("Fikk ikke hentet token til UlfKomponent fra Maskinporten")
        }
    } catch (e: Exception) {
        log.error("UlfKonsument - feilet ved uthenting av token maskinporten", e)
        throw e
    }

    override fun hentEndringer() {
        val token = getToken()
        // val enddringer_flyktningsstatus= "$URL/personer/$id?requestedFields=JuridiskStatus_Flyktningstatus" TODO

        val getRequest = HttpRequest.newBuilder()
            .uri(URI.create(URL_PING))
            .header("Accept", "application/json")
            .header("Authorization", "Bearer $token")
            // .header("Nav-Call-Id", correlationId.toString())
            .header("Content-Type", "application/json")
            // .POST(HttpRequest.BodyPublishers.ofString(requestBody))
            .GET()
            .build()

        val httpRespons = client.send(getRequest, HttpResponse.BodyHandlers.ofString())
        if (httpRespons.isSuccess()) {
            val statusCode = httpRespons.statusCode()
            val body: String = httpRespons.body()
            log.info("UlfKonsument - Fikk en OK status response $statusCode fra Ulf, body=$body")
        } else {
            val statusCode = httpRespons.statusCode()
            val body: String = httpRespons.body()
            log.error("UlfKonsument - Fikk en feilet status response $statusCode fra Ulf, body=$body")
        }
    }
}

fun <T> HttpResponse<T>.isSuccess(): Boolean = this.statusCode() in 200..299
