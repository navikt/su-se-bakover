package no.nav.su.se.bakover.client.representasjon

import kotlinx.coroutines.future.await
import no.nav.su.se.bakover.common.domain.auth.AccessToken
import no.nav.su.se.bakover.common.person.Fnr
import java.net.URI
import java.net.http.HttpRequest
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

class RepresentasjonHttpClient(
    endepunkt: String,
    private val getToken: suspend () -> AccessToken,
    connectTimeout: Duration = 1.seconds,
    private val timeout: Duration = 1.seconds,
) {
    private val client =
        java.net.http.HttpClient
            .newBuilder()
            .connectTimeout(connectTimeout.toJavaDuration())
            .followRedirects(java.net.http.HttpClient.Redirect.NEVER)
            .build()

    private val uri = URI.create("$endepunkt/api/internbruker/fullmaktsgiver")

    suspend fun hentFullmaktsgiver(
        fnr: Fnr,
    ): String {
        val request = createRequest(fnr)

        val httpResponse = client.sendAsync(request, java.net.http.HttpResponse.BodyHandlers.ofString()).await()
        val body = httpResponse.body()
        return httpResponse.body()
    }

    private suspend fun RepresentasjonHttpClient.createRequest(fnr: Fnr): HttpRequest? =
        HttpRequest.newBuilder()
            .uri(uri)
            .header("Authorization", "Bearer ${getToken().value}")
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString("""{"ident": "${fnr.verdi}"}"""))
            .build()
}
