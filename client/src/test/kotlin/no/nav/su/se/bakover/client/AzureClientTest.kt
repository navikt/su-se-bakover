package no.nav.su.se.bakover.client

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import no.nav.su.se.bakover.client.AzureClient.Companion.AZURE_ON_BEHALF_OF_GRANT_TYPE
import no.nav.su.se.bakover.client.AzureClient.Companion.REQUESTED_TOKEN_USE
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.net.URLEncoder
import java.nio.charset.Charset

private const val CLIENT_ID = "clientId"
private const val CLIENT_SECRET = "clientSecret"
private const val TOKEN_TO_EXCHANGE = "eyJabc123"
private const val EXCHANGED_TOKEN = "exchanged"
private const val TOKEN_ENDPOINT_PATH = "/oauth2/v2.0/token"
private const val SCOPE = "personClientId"

internal class AzureClientKtTest {

    val azureClient = AzureClient(
            CLIENT_ID,
            CLIENT_SECRET,
            "${wireMockServer.baseUrl()}$TOKEN_ENDPOINT_PATH"
    )

    @Test
    fun `exchange token ok response`() {
        wireMockServer.stubFor(post(urlPathEqualTo(TOKEN_ENDPOINT_PATH))
                .withHeader("Content-Type", equalTo("application/x-www-form-urlencoded"))
                .withRequestBody(containing("grant_type=${urlEncode(AZURE_ON_BEHALF_OF_GRANT_TYPE)}"))
                .withRequestBody(containing("client_id=$CLIENT_ID"))
                .withRequestBody(containing("client_secret=$CLIENT_SECRET"))
                .withRequestBody(containing("assertion=$TOKEN_TO_EXCHANGE"))
                .withRequestBody(containing("scope=$SCOPE${urlEncode("/.default")}"))
                .withRequestBody(containing("requested_token_use=$REQUESTED_TOKEN_USE"))
                .willReturn(okJson(okAzureResponse)))

        val exchangedToken: String = azureClient.onBehalfOFToken(TOKEN_TO_EXCHANGE, "personClientId")
        assertEquals(EXCHANGED_TOKEN, exchangedToken)
    }

    @Test
    fun `exchange token error response`() {
        wireMockServer.stubFor(post(urlPathEqualTo(TOKEN_ENDPOINT_PATH))
                .willReturn(okJson(errorAzureResponse)))
        assertThrows<RuntimeException> {
            azureClient.onBehalfOFToken(TOKEN_TO_EXCHANGE, "someAppId")
        }
    }

    private fun urlEncode(string: String): String {
        return URLEncoder.encode(string, Charset.defaultCharset())
    }

    val okAzureResponse = """
        {
          "token_type": "Bearer",
          "scope": "some scope",
          "expires_in": 3600,
          "ext_expires_in": 0,
          "access_token": "$EXCHANGED_TOKEN",
          "refresh_token": "exchanged refresh token"
        }
    """.trimIndent()

    val errorAzureResponse = """
        {
            "error":"interaction_required",
            "error_description":"blah blah some descriptive messge",
            "error_codes":[50079],
            "timestamp":"2017-05-01 22:43:20Z",
            "trace_id":"b72a68c3-0926-4b8e-bc35-3150069c2800",
            "correlation_id":"73d656cf-54b1-4eb2-b429-26d8165a52d7",
            "claims":"{\"access_token\":{\"polids\":{\"essential\":true,\"values\":[\"9ab03e19-ed42-4168-b6b7-7001fb3e933a\"]}}}"
        }
    """.trimIndent()

    companion object {
        val wireMockServer: WireMockServer = WireMockServer(WireMockConfiguration.options().dynamicPort())

        @BeforeAll
        @JvmStatic
        fun start() {
            wireMockServer.start()
        }

        @AfterAll
        @JvmStatic
        fun stop() {
            wireMockServer.stop()
        }
    }
}