package no.nav.su.se.bakover.client.sts

import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.client.WiremockBase
import no.nav.su.se.bakover.client.WiremockBase.Companion.wireMockServer
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class StsClientTest : WiremockBase {
    val username = "srvsupstonad"
    val password = "supersecret"

    @Test
    fun `stsClientTest`() {
        val client = StsClient(wireMockServer.baseUrl(), username, password)
        client.token() shouldBe "token"
    }

    @BeforeEach
    fun setup() {
        wireMockServer.stubFor(
            WireMock.get(urlPathEqualTo("/rest/v1/sts/token"))
                .withBasicAuth(username, password)
                .withHeader("Accept", equalTo("application/json"))
                .withQueryParam("grant_type", equalTo("client_credentials"))
                .withQueryParam("scope", equalTo("openid"))
                .willReturn(
                    WireMock.okJson(
                        """
                            {
                                "access_token": "token",
                                "expires_in": "3600"
                            }
                        """.trimIndent()
                    )
                )
        )
    }
}
