package no.nav.su.se.bakover.client.nais

import com.github.tomakehurst.wiremock.client.WireMock
import io.kotest.assertions.arrow.either.shouldBeLeft
import io.kotest.assertions.arrow.either.shouldBeRight
import no.nav.su.se.bakover.client.WiremockBase
import no.nav.su.se.bakover.client.WiremockBase.Companion.wireMockServer
import no.nav.su.se.bakover.domain.nais.LeaderPodLookupFeil
import org.junit.jupiter.api.Test

internal class LeaderPodLookupClientTest : WiremockBase {

    @Test
    fun `sier ja når leader elector-pod svarer at vårt hostname er leader`() {
        val endpoint = "/am/i/the/leader"
        val localHostName = "jacob.local"

        wireMockServer.stubFor(
            WireMock.get(WireMock.urlPathEqualTo(endpoint))
                .willReturn(
                    WireMock.ok(
                        //language=JSON
                        """
                            {
                              "name": $localHostName
                            }
                        """.trimIndent()
                    )
                )
        )

        LeaderPodLookupClient.amITheLeader("${wireMockServer.baseUrl()}$endpoint", localHostName) shouldBeRight true
    }

    @Test
    fun `sier nei når leader elector-pod svarer at noen andre er leader`() {
        val endpoint = "/am/i/the/leader"
        val localHostName = "jacob.local"

        wireMockServer.stubFor(
            WireMock.get(WireMock.urlPathEqualTo(endpoint))
                .willReturn(
                    WireMock.ok(
                        //language=JSON
                        """
                            {
                              "name": "foooooo"
                            }
                        """.trimIndent()
                    )
                )
        )

        LeaderPodLookupClient.amITheLeader("${wireMockServer.baseUrl()}$endpoint", localHostName) shouldBeRight false
    }

    @Test
    fun `håndterer ugyldig json`() {
        val endpoint = "/am/i/the/leader"
        val localHostName = "jacob"

        wireMockServer.stubFor(
            WireMock.get(WireMock.urlPathEqualTo(endpoint))
                .willReturn(
                    WireMock.ok(
                        //language=JSON
                        """
                            {
                              "foo": "bar"
                            }
                        """.trimIndent()
                    )
                )
        )

        LeaderPodLookupClient.amITheLeader("${wireMockServer.baseUrl()}$endpoint", localHostName) shouldBeLeft LeaderPodLookupFeil.UkjentSvarFraLeaderElectorContainer
    }
}
