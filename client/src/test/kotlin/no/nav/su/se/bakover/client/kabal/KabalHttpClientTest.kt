package no.nav.su.se.bakover.client.kabal

import arrow.core.right
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.client.WiremockBase
import no.nav.su.se.bakover.client.WiremockBase.Companion.wireMockServer
import no.nav.su.se.bakover.client.argThat
import no.nav.su.se.bakover.client.azure.OAuth
import no.nav.su.se.bakover.client.sts.TokenOppslag
import no.nav.su.se.bakover.common.ApplicationConfig
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.test.iverksattKlage
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.skyscreamer.jsonassert.JSONAssert
import org.slf4j.MDC

internal class KabalHttpClientTest : WiremockBase {

    @Test
    fun `opprett sakbehandling oppgave ny periode`() {
        wireMockServer.stubFor(
            stubMapping.willReturn(
                aResponse()
                    .withBody("")
                    .withStatus(201),
            ),
        )
        val oathMock = mock<OAuth> {
            on { onBehalfOfToken(any(), any()) } doReturn "token"
        }
        val tokenoppslagMock = mock<TokenOppslag> {
            on { token() } doReturn "token"
        }
        val client = KabalHttpClient(
            kabalConfig = ApplicationConfig.ClientsConfig.KabalConfig(
                clientId = "kabalClientId",
                url = wireMockServer.baseUrl(),
            ),
            exchange = oathMock,
        )
        val klage = iverksattKlage().second
        client.sendTilKlageinstans(
            klage = klage,
            journalpostIdForVedtak = JournalpostId(value = ""),
        ) shouldBe Unit.right()

        verify(oathMock).onBehalfOfToken(
            originalToken = argThat { it shouldBe "Bearer token" },
            otherAppId = argThat { it shouldBe "kabalClientId" },
        )
        verifyNoMoreInteractions(oathMock, tokenoppslagMock)
        val actualRequest = wireMockServer.allServeEvents.first().request.bodyAsString
        //language=JSON
        val expectedRequest = """
        {
          "avsenderEnhet":"4815",
          "avsenderSaksbehandlerIdent":"saksbehandler",
          "dvhReferanse":"${klage.id}",
          "fagsak":{
            "fagsakId":"12345676",
            "fagsystem":"SUPSTONAD"
          },
          "hjemler":[
            {
              "kapittel":2,
              "lov":"SUPPLERENDE_STONAD",
              "paragraf":3
            },
            {
              "kapittel":2,
              "lov":"SUPPLERENDE_STONAD",
              "paragraf":4
            }
          ],
          "innsendtTilNav":"2021-12-01",
          "mottattFoersteinstans":"2021-01-01",
          "kilde":"su-se-bakover",
          "kildeReferanse":"${klage.id}",
          "klager":{
            "id":{
              "type":"PERSON",
              "verdi":"${klage.fnr}"
            }
          },
          "tilknyttedeJournalposter":[
            {
              "journalpostId":"klageJournalpostId",
              "type":"BRUKERS_KLAGE"
            },
            {
              "journalpostId":"",
              "type":"OPPRINNELIG_VEDTAK"
            }
          ],
          "kommentar":null,
          "frist":null,
          "sakenGjelder":null,
          "oversendtKaDato":null,
          "innsynUrl":null,
          "type":"KLAGE",
          "ytelse":"SUP_UFF"
        }
        """.trimIndent()
        println(actualRequest)
        JSONAssert.assertEquals(expectedRequest, actualRequest, true)
    }

    private val stubMapping = WireMock.post(urlPathEqualTo(oversendelsePath))
        .withHeader("Authorization", WireMock.equalTo("Bearer token"))
        .withHeader("X-Correlation-ID", WireMock.equalTo("correlationId"))
        .withHeader("Accept", WireMock.equalTo("application/json"))
        .withHeader("Content-Type", WireMock.equalTo("application/json"))

    @BeforeEach
    fun beforeEach() {
        MDC.put("Authorization", "Bearer token")
    }
}
