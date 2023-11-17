package no.nav.su.se.bakover.client.sts

import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.SU_SE_BAKOVER_CONSUMER_ID
import no.nav.su.se.bakover.common.domain.auth.AccessToken
import no.nav.su.se.bakover.test.wiremock.startedWireMockServerWithCorrelationId
import org.junit.jupiter.api.Test
import java.time.Clock

internal class StsClientTest {

    private val username = SU_SE_BAKOVER_CONSUMER_ID
    private val password = "supersecret"

    @Test
    fun stsClientTest() {
        startedWireMockServerWithCorrelationId {
            stubFor(
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
                            """.trimIndent(),
                        ),
                    ),
            )
            val client = StsClient(baseUrl(), username, password, clock = Clock.systemUTC())
            client.token() shouldBe AccessToken("token")
        }
    }
}
