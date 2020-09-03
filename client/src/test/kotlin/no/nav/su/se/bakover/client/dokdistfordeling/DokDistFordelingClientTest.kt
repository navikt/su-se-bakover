package no.nav.su.se.bakover.client.dokdistfordeling

import arrow.core.right
import com.github.tomakehurst.wiremock.client.WireMock
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.client.WiremockBase
import no.nav.su.se.bakover.client.WiremockBase.Companion.wireMockServer
import no.nav.su.se.bakover.client.stubs.sts.TokenOppslagStub
import org.junit.jupiter.api.Test

internal class DokDistFordelingClientTest : WiremockBase {
    val journalId = "1"
    val client = DokDistFordelingClient(wireMockServer.baseUrl(),TokenOppslagStub)

    val requestBody = client.byggDistribusjonPostJson(journalId)

    @Test
    fun `should complete order for distribution`() {
        wireMockServer.stubFor(
            wiremockBuilder
                .withRequestBody(WireMock.equalToJson(requestBody))
                .willReturn(
                    WireMock.okJson(
                        """
                        {
                            "bestillingsId": "id på tingen"
                        }
                        """.trimIndent()
                    )
                )
        )
        client.bestillDistribusjon(journalId) shouldBe "id på tingen".right()

    }
    val wiremockBuilder = WireMock.post(WireMock.urlPathEqualTo(dokDistFordelingPath))
        .withHeader("Authorization", WireMock.equalTo("Bearer token"))
        .withHeader("Content-Type", WireMock.equalTo("application/json"))
        .withHeader("Accept", WireMock.equalTo("application/json"))
        .withHeader("X-Correlation-ID", WireMock.equalTo("correlationId"))
}
