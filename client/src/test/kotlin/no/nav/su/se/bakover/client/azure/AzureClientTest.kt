package no.nav.su.se.bakover.client.azure

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import no.nav.su.se.bakover.client.azure.AzureClient.Companion.AZURE_ON_BEHALF_OF_GRANT_TYPE
import no.nav.su.se.bakover.client.azure.AzureClient.Companion.REQUESTED_TOKEN_USE
import no.nav.su.se.bakover.common.auth.AzureAd
import no.nav.su.se.bakover.test.wiremock.startedWireMockServerWithCorrelationId
import org.junit.jupiter.api.Assertions.assertEquals
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
private const val JWKS_PATH = "/keys"
private const val WELLKNOWN_URL = "/.well-known"
private const val ISSUER = "azure"

internal class AzureClientTest {

    @Test
    fun `exchange to on-behalf-of token`() {
        startedWireMockServerWithCorrelationId {
            stubGetJwk()
            stubFor(
                WireMock.post(
                    WireMock.urlPathEqualTo(
                        TOKEN_ENDPOINT_PATH,
                    ),
                )
                    .withHeader("Content-Type", WireMock.equalTo("application/x-www-form-urlencoded"))
                    .withRequestBody(WireMock.containing("grant_type=${urlEncode(AZURE_ON_BEHALF_OF_GRANT_TYPE)}"))
                    .withRequestBody(WireMock.containing("client_id=$CLIENT_ID"))
                    .withRequestBody(WireMock.containing("client_secret=$CLIENT_SECRET"))
                    .withRequestBody(WireMock.containing("assertion=$TOKEN_TO_EXCHANGE"))
                    .withRequestBody(WireMock.containing("scope=$SCOPE${urlEncode("/.default")}"))
                    .withRequestBody(WireMock.containing("requested_token_use=$REQUESTED_TOKEN_USE"))
                    .willReturn(WireMock.okJson(okAzureResponse)),
            )

            val exchangedToken: String = oauth(baseUrl()).onBehalfOfToken(TOKEN_TO_EXCHANGE, "personClientId")
            assertEquals(EXCHANGED_TOKEN, exchangedToken)
        }
    }

    @Test
    fun `get issuer`() {
        startedWireMockServerWithCorrelationId {
            stubGetJwk()
            assertEquals(ISSUER, oauth(baseUrl()).issuer)
        }
    }

    @Test
    fun `exchange token error response`() {
        startedWireMockServerWithCorrelationId {
            stubGetJwk()
            stubFor(
                WireMock.post(
                    WireMock.urlPathEqualTo(
                        TOKEN_ENDPOINT_PATH,
                    ),
                ).willReturn(WireMock.okJson(errorAzureResponse)),
            )
            assertThrows<RuntimeException> { oauth(baseUrl()).onBehalfOfToken(TOKEN_TO_EXCHANGE, "someAppId") }
        }
    }

    private fun urlEncode(string: String): String {
        return URLEncoder.encode(string, Charset.defaultCharset())
    }

    private val okAzureResponse =
        """
        {
          "token_type": "Bearer",
          "scope": "some scope",
          "expires_in": 3600,
          "ext_expires_in": 0,
          "access_token": "$EXCHANGED_TOKEN",
          "refresh_token": "exchanged refresh token"
        }
        """.trimIndent()

    private val errorAzureResponse =
        """
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

    private fun WireMockServer.stubGetJwk() = stubFor(
        WireMock.get(WireMock.urlPathEqualTo(WELLKNOWN_URL)).willReturn(
            WireMock.okJson(
                """
                    {
                        "jwks_uri": "${baseUrl()}$JWKS_PATH",
                        "token_endpoint": "${baseUrl()}$TOKEN_ENDPOINT_PATH",
                        "issuer": "$ISSUER"
                    }
                """.trimIndent(),
            ),
        ),
    )

    private fun oauth(baseUrl: String): AzureAd =
        AzureClient(
            thisClientId = CLIENT_ID,
            thisClientSecret = CLIENT_SECRET,
            wellknownUrl = "${baseUrl}$WELLKNOWN_URL",
        )
}
