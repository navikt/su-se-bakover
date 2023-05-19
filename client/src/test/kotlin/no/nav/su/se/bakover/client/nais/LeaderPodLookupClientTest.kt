package no.nav.su.se.bakover.client.nais

import arrow.core.left
import arrow.core.right
import com.github.tomakehurst.wiremock.client.WireMock
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.client.WiremockBase
import no.nav.su.se.bakover.client.WiremockBase.Companion.wireMockServer
import no.nav.su.se.bakover.common.nais.LeaderPodLookupFeil
import org.junit.jupiter.api.Test

internal class LeaderPodLookupClientTest : WiremockBase {

    private val endpoint = "/am/i/the/leader"
    private val localHostName = "localhost"

    @Test
    fun `sier ja når leader elector-pod svarer at vårt hostname er leader`() {
        wireMockServer.stubFor(
            WireMock.get(WireMock.urlPathEqualTo(endpoint))
                .willReturn(
                    WireMock.ok(
                        """
                            {
                              "name": $localHostName
                            }
                        """.trimIndent(),
                    ),
                ),
        )

        LeaderPodLookupClient("${wireMockServer.baseUrl()}$endpoint").amITheLeader(localHostName) shouldBe true.right()
    }

    @Test
    fun `sier nei når leader elector-pod svarer at noen andre er leader`() {
        wireMockServer.stubFor(
            WireMock.get(WireMock.urlPathEqualTo(endpoint))
                .willReturn(
                    WireMock.ok(
                        """
                            {
                              "name": "foooooo"
                            }
                        """.trimIndent(),
                    ),
                ),
        )

        LeaderPodLookupClient("${wireMockServer.baseUrl()}$endpoint").amITheLeader(localHostName) shouldBe false.right()
    }

    @Test
    fun `håndterer ugyldig json`() {
        wireMockServer.stubFor(
            WireMock.get(WireMock.urlPathEqualTo(endpoint))
                .willReturn(
                    WireMock.ok(
                        """
                            {
                              "foo": "bar"
                            }
                        """.trimIndent(),
                    ),
                ),
        )

        LeaderPodLookupClient("${wireMockServer.baseUrl()}$endpoint").amITheLeader(localHostName) shouldBe LeaderPodLookupFeil.UkjentSvarFraLeaderElectorContainer.left()
    }
}
