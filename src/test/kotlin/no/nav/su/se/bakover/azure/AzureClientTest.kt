package no.nav.su.se.bakover.azure

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import io.ktor.http.ContentType.Application.FormUrlEncoded
import io.ktor.http.HttpHeaders.ContentType
import no.nav.su.se.bakover.azure.AzureClient.Companion.GRANT_TYPE
import no.nav.su.se.bakover.azure.AzureClient.Companion.REQUESTED_TOKEN_USE
import org.junit.jupiter.api.*
import kotlin.test.assertEquals

const val CLIENT_ID = "clientId"
const val CLIENT_SECRET = "clientSecret"
const val TOKEN_TO_EXCHANGE = "eyJabc123"
const val EXCHANGED_TOKEN = "exchanged"
const val TOKEN_ENDPOINT_PATH = "/oauth2/v2.0/token"

internal class AzureClientKtTest {

    val azureClient = AzureClient(CLIENT_ID, CLIENT_SECRET, "${wireMockServer.baseUrl()}$TOKEN_ENDPOINT_PATH")

    @Test
    fun `exchange token ok response`() {
        stubFor(post(urlPathEqualTo(TOKEN_ENDPOINT_PATH))
                .withHeader(ContentType, equalTo(FormUrlEncoded.toString()))
                .withRequestBody(matchingJsonPath("$[?(@.grant_type == '$GRANT_TYPE')]"))
                .withRequestBody(matchingJsonPath("$[?(@.client_id == '$CLIENT_ID')]"))
                .withRequestBody(matchingJsonPath("$[?(@.client_secret == '$CLIENT_SECRET')]"))
                .withRequestBody(matchingJsonPath("$[?(@.assertion == '$TOKEN_TO_EXCHANGE')]"))
                .withRequestBody(matchingJsonPath("$[?(@.scope == 'some scope')]"))
                .withRequestBody(matchingJsonPath("$[?(@.requested_token_use == '$REQUESTED_TOKEN_USE')]"))
                .willReturn(okJson(okAzureResponse)))

        val exchangedToken: String = azureClient.exchangeToken(TOKEN_TO_EXCHANGE)
        assertEquals(EXCHANGED_TOKEN, exchangedToken)
    }

    @Test
    fun `exchange token error response`() {
        stubFor(post(urlPathEqualTo(TOKEN_ENDPOINT_PATH))
                .willReturn(okJson(errorAzureResponse)))
        assertThrows<RuntimeException> {
            azureClient.exchangeToken(TOKEN_TO_EXCHANGE)
        }
    }

    val okAzureResponse = """
        {
          "token_type": "Bearer",
          "scope": "some scope",
          "expires_in": 3600,
          "ext_expires_in": 0,
          "access_token": "$EXCHANGED_TOKEN",
          "refresh_token": "OAQABAAAAAABnfiG-mA6NTae7CdWW7QfdAALzDWjw6qSn4GUDfxWzJDZ6lk9qRw4AnqPnvFqnzS3GiikHr5wBM1bV1YyjH3nUeIhKhqJWGwqJFRqs2sE_rqUfz7__3J92JDpi6gDdCZNNaXgreQsH89kLCVNYZeN6kGuFGZrjwxp1wS2JYc97E_3reXBxkHrA09K5aR-WsSKCEjf6WI23FhZMTLhk_ZKOe_nWvcvLj13FyvSrTMZV2cmzyCZDqEHtPVLJgSoASuQlD2NXrfmtcmgWfc3uJSrWLIDSn4FEmVDA63X6EikNp9cllH3Gp7Vzapjlnws1NQ1_Ff5QrmBHp_LKEIwfzVKnLLrQXN0EzP8f6AX6fdVTaeKzm7iw6nH0vkPRpUeLc3q_aNsPzqcTOnFfgng7t2CXUsMAGH5wclAyFCAwL_Cds7KnyDLL7kzOS5AVZ3Mqk2tsPlqopAiHijZaJumdTILDudwKYCFAMpUeUwEf9JmyFjl2eIWPmlbwU7cHKWNvuRCOYVqbsTTpJthwh4PvsL5ov5CawH_TaV8omG_tV6RkziHG9urk9yp2PH9gl7Cv9ATa3Vt3PJWUS8LszjRIAJmyw_EhgHBfYCvEZ8U9PYarvgqrtweLcnlO7BfnnXYEC18z_u5wemAzNBFUje2ttpGtRmRic4AzZ708tBHva2ePJWGX6pgQbiWF8esOrvWjfrrlfOvEn1h6YiBW291M022undMdXzum6t1Y1huwxHPHjCAA"
        }
    """.trimIndent()

    val errorAzureResponse = """
        {
            "error":"interaction_required",
            "error_description":"AADSTS50079: Due to a configuration change made by your administrator, or because you moved to a new location, you must enroll in multi-factor authentication to access 'bf8d80f9-9098-4972-b203-500f535113b1'.\r\nTrace ID: b72a68c3-0926-4b8e-bc35-3150069c2800\r\nCorrelation ID: 73d656cf-54b1-4eb2-b429-26d8165a52d7\r\nTimestamp: 2017-05-01 22:43:20Z",
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

    @BeforeEach
    fun configure() {
        configureFor(wireMockServer.port())
    }
}