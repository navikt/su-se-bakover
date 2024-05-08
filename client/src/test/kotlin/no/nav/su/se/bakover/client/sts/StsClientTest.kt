package no.nav.su.se.bakover.client.sts

import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import io.kotest.matchers.equality.shouldBeEqualToComparingFields
import no.nav.su.se.bakover.test.wiremock.startedWireMockServerWithCorrelationId
import org.json.JSONObject
import org.junit.jupiter.api.Test

internal class StsClientTest {

    @Test
    fun stsClientTest() {
        val payload = """
        {
          "jwks_uri":"http://localhost:8080/jwks"
        }
        """.trimIndent()
        startedWireMockServerWithCorrelationId {
            stubFor(
                WireMock.get(urlPathEqualTo("/.well-known/openid-configuration"))
                    .willReturn(
                        WireMock.okJson(
                            payload,
                        ),
                    ),
            )
            val client = StsClient(baseUrl())
            client.jwkConfig().shouldBeEqualToComparingFields(JSONObject(payload))
        }
    }
}
