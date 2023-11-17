package no.nav.su.se.bakover.client.kabal

import arrow.core.left
import arrow.core.right
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.http.Fault
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.client.argThat
import no.nav.su.se.bakover.common.auth.AzureAd
import no.nav.su.se.bakover.common.domain.auth.AccessToken
import no.nav.su.se.bakover.common.domain.auth.TokenOppslag
import no.nav.su.se.bakover.common.infrastructure.config.ApplicationConfig
import no.nav.su.se.bakover.common.journal.JournalpostId
import no.nav.su.se.bakover.domain.klage.KunneIkkeOversendeTilKlageinstans
import no.nav.su.se.bakover.test.oversendtKlage
import no.nav.su.se.bakover.test.wiremock.startedWireMockServerWithCorrelationId
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.skyscreamer.jsonassert.JSONAssert

internal class KabalHttpClientTest {

    private val klage = oversendtKlage().second
    private val expectedRequest = """
        {
          "klager":{
            "id":{
              "type":"PERSON",
              "verdi":"${klage.fnr}"
            }
          },
          "fagsak":{
            "fagsakId":"12345676",
            "fagsystem":"SUPSTONAD"
          },
          "kildeReferanse":"${klage.id}",
          "dvhReferanse":"${klage.id}",
          "hjemler":[
            "SUP_ST_L_3",
            "SUP_ST_L_4"
          ],
            "tilknyttedeJournalposter":[
            {
              "journalpostId":"klageJournalpostId",
              "type":"BRUKERS_KLAGE"
            },
            {
              "journalpostId":"journalpostIdForVedtak",
              "type":"OPPRINNELIG_VEDTAK"
            }
          ],
          "brukersHenvendelseMottattNavDato":"2021-01-15",
          "innsendtTilNav":"2021-01-15",
          "type":"KLAGE",
          "forrigeBehandlendeEnhet":"4815",
          "kilde":"SUPSTONAD",
          "ytelse":"SUP_UFF"
        }
    """.trimIndent()

    @Test
    fun `500 case`() {
        startedWireMockServerWithCorrelationId {
            this.stubFor(
                stubMapping.willReturn(
                    aResponse()
                        .withBody("")
                        .withStatus(500),
                ),
            )
            val oathMock = mock<AzureAd> {}
            val tokenoppslagMock = mock<TokenOppslag> {
                on { token() } doReturn AccessToken("token")
            }
            val client = KabalHttpClient(
                kabalConfig = ApplicationConfig.ClientsConfig.KabalConfig(
                    clientId = "kabalClientId",
                    url = this.baseUrl(),
                ),
                exchange = oathMock,
            )
            client.sendTilKlageinstans(
                klage = klage,
                journalpostIdForVedtak = JournalpostId(value = "journalpostIdForVedtak"),
            ) shouldBe KunneIkkeOversendeTilKlageinstans.left()

            verify(oathMock).getSystemToken(
                otherAppId = argThat { it shouldBe "kabalClientId" },
            )
            verifyNoMoreInteractions(oathMock, tokenoppslagMock)
            val actualRequest = allServeEvents.first().request.bodyAsString

            JSONAssert.assertEquals(expectedRequest, actualRequest, true)
        }
    }

    @Test
    fun `Connection error case`() {
        startedWireMockServerWithCorrelationId {
            stubFor(
                stubMapping.willReturn(
                    aResponse()
                        .withFault(Fault.CONNECTION_RESET_BY_PEER),
                ),
            )
            val oathMock = mock<AzureAd> {
                on { getSystemToken(any()) } doReturn "token"
            }

            val client = KabalHttpClient(
                kabalConfig = ApplicationConfig.ClientsConfig.KabalConfig(
                    clientId = "kabalClientId",
                    url = baseUrl(),
                ),
                exchange = oathMock,
            )
            client.sendTilKlageinstans(
                klage = klage,
                journalpostIdForVedtak = JournalpostId(value = "journalpostIdForVedtak"),
            ) shouldBe KunneIkkeOversendeTilKlageinstans.left()

            verify(oathMock).getSystemToken(
                otherAppId = argThat { it shouldBe "kabalClientId" },
            )
            verifyNoMoreInteractions(oathMock)
            val actualRequest = allServeEvents.first().request.bodyAsString

            JSONAssert.assertEquals(expectedRequest, actualRequest, true)
        }
    }

    @Test
    fun `success case`() {
        startedWireMockServerWithCorrelationId {
            stubFor(
                stubMapping.willReturn(
                    aResponse()
                        .withBody("")
                        .withStatus(201),
                ),
            )
            val oathMock = mock<AzureAd> {
                on { getSystemToken(any()) } doReturn "token"
            }

            val client = KabalHttpClient(
                kabalConfig = ApplicationConfig.ClientsConfig.KabalConfig(
                    clientId = "kabalClientId",
                    url = baseUrl(),
                ),
                exchange = oathMock,
            )
            client.sendTilKlageinstans(
                klage = klage,
                journalpostIdForVedtak = JournalpostId(value = "journalpostIdForVedtak"),
            ) shouldBe Unit.right()

            verify(oathMock).getSystemToken(
                otherAppId = argThat { it shouldBe "kabalClientId" },
            )
            verifyNoMoreInteractions(oathMock)
            val actualRequest = allServeEvents.first().request.bodyAsString
            JSONAssert.assertEquals(expectedRequest, actualRequest, true)
        }
    }

    private val stubMapping = WireMock.post(urlPathEqualTo(OVERSENDELSE_PATH))
        .withHeader("Authorization", WireMock.equalTo("Bearer token"))
        .withHeader("Nav-Callid", WireMock.equalTo("correlationId"))
        .withHeader("Accept", WireMock.equalTo("application/json"))
        .withHeader("Content-Type", WireMock.equalTo("application/json"))
}
