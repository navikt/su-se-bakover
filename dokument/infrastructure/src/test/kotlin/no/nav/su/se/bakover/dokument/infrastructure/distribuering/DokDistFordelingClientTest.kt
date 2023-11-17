package no.nav.su.se.bakover.dokument.infrastructure.distribuering

import arrow.core.right
import com.github.tomakehurst.wiremock.client.MappingBuilder
import com.github.tomakehurst.wiremock.client.WireMock
import dokument.domain.Distribusjonstidspunkt
import dokument.domain.Distribusjonstype
import dokument.domain.brev.BrevbestillingId
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.infrastructure.auth.TokenOppslagStub
import no.nav.su.se.bakover.common.journal.JournalpostId
import no.nav.su.se.bakover.test.wiremock.startedWireMockServerWithCorrelationId
import org.junit.jupiter.api.Test

internal class DokDistFordelingClientTest {

    @Test
    fun `should complete order for distribution`() {
        startedWireMockServerWithCorrelationId {
            val journalpostId = JournalpostId("1")
            val distribusjonstype = Distribusjonstype.VEDTAK
            val distribusjonstidspunkt = Distribusjonstidspunkt.KJERNETID
            val client = DokDistFordelingClient(baseUrl(), TokenOppslagStub)
            val requestBody = client.byggDistribusjonPostJson(journalpostId, distribusjonstype, distribusjonstidspunkt)
            stubFor(
                wiremockBuilder
                    .withRequestBody(WireMock.equalToJson(requestBody))
                    .willReturn(
                        WireMock.okJson(
                            """
                        {
                            "bestillingsId": "id på tingen"
                        }
                            """.trimIndent(),
                        ),
                    ),
            )
            client.bestillDistribusjon(
                journalpostId,
                distribusjonstype,
                distribusjonstidspunkt,
            ) shouldBe BrevbestillingId("id på tingen").right()
        }
    }

    private val wiremockBuilder: MappingBuilder = WireMock.post(WireMock.urlPathEqualTo(DOK_DIST_FORDELING_PATH))
        .withHeader("Authorization", WireMock.equalTo("Bearer token"))
        .withHeader("Content-Type", WireMock.equalTo("application/json"))
        .withHeader("Accept", WireMock.equalTo("application/json"))
        .withHeader("Nav-CallId", WireMock.equalTo("correlationId"))
}
