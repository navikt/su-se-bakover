package no.nav.su.se.bakover.client.nais

import arrow.core.left
import arrow.core.right
import com.github.tomakehurst.wiremock.client.WireMock
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.nais.LeaderPodLookupFeil
import no.nav.su.se.bakover.test.wiremock.startedWireMockServerWithCorrelationId
import org.junit.jupiter.api.Test

internal class LeaderPodLookupClientTest {

    private val endpoint = "/am/i/the/leader"
    private val localHostName = "localhost"

    @Test
    fun `sier ja når leader elector-pod svarer at vårt hostname er leader`() {
        startedWireMockServerWithCorrelationId {
            stubFor(
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

            LeaderPodLookupClient("${baseUrl()}$endpoint").amITheLeader(localHostName) shouldBe true.right()
        }
    }

    @Test
    fun `sier nei når leader elector-pod svarer at noen andre er leader`() {
        startedWireMockServerWithCorrelationId {
            stubFor(
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

            LeaderPodLookupClient("${baseUrl()}$endpoint").amITheLeader(localHostName) shouldBe false.right()
        }
    }

    @Test
    fun `håndterer ugyldig json`() {
        startedWireMockServerWithCorrelationId {
            stubFor(
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

            LeaderPodLookupClient("${baseUrl()}$endpoint").amITheLeader(localHostName) shouldBe LeaderPodLookupFeil.UkjentSvarFraLeaderElectorContainer.left()
        }
    }
}
