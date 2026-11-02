package no.nav.su.se.bakover.dokument.infrastructure.client

import arrow.core.left
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.http.Fault
import dokument.domain.PdfByggerKlientFeil
import dokument.domain.generer.bygger.dokument
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.auth.AzureAd
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.wiremock.startedWireMockServerWithCorrelationId
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

internal class PdfByggerKlientImplTest {
    private val clientId = "pdf-bygger"
    private val systemToken = "system-token"
    private val azureAd = mock<AzureAd> {
        on { getSystemToken(clientId) } doReturn systemToken
    }
    private val bestilling = dokument(tittel = "Testdokument", personalia = null) {}
    private val sti = "/produserDokument"

    @Test
    fun `tokenfeil returneres som typet feil og et senere forsøk kan lykkes`() {
        startedWireMockServerWithCorrelationId {
            val pdfBytes = "pdf".toByteArray()
            stubFor(
                WireMock.post(WireMock.urlPathEqualTo(sti))
                    .willReturn(WireMock.okJson(serialize(PdfResponse(pdfBytes)))),
            )
            whenever(azureAd.getSystemToken(clientId))
                .thenThrow(RuntimeException("Tokenhenting feilet"))
                .thenReturn(systemToken)
            val klient = PdfByggerKlientImpl(baseUrl(), azureAd, clientId)

            klient.lagPdf(bestilling) shouldBe PdfByggerKlientFeil.left()
            verify(0, WireMock.postRequestedFor(WireMock.urlPathEqualTo(sti)))

            klient.lagPdf(bestilling).getOrFail().toList() shouldBe pdfBytes.toList()
            verify(
                1,
                WireMock.postRequestedFor(WireMock.urlPathEqualTo(sti))
                    .withHeader("Authorization", WireMock.equalTo("Bearer $systemToken"))
                    .withRequestBody(WireMock.equalToJson(serialize(bestilling.request))),
            )
        }
    }

    @Test
    fun `utilgjengelig tjeneste returneres som typet feil`() {
        startedWireMockServerWithCorrelationId {
            stubFor(
                WireMock.post(WireMock.urlPathEqualTo(sti))
                    .willReturn(WireMock.aResponse().withStatus(503)),
            )
            val klient = PdfByggerKlientImpl(baseUrl(), azureAd, clientId)

            klient.lagPdf(bestilling) shouldBe PdfByggerKlientFeil.left()
        }
    }

    @Test
    fun `brutt forbindelse returneres som typet feil`() {
        startedWireMockServerWithCorrelationId {
            stubFor(
                WireMock.post(WireMock.urlPathEqualTo(sti))
                    .willReturn(WireMock.aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)),
            )
            val klient = PdfByggerKlientImpl(baseUrl(), azureAd, clientId)

            klient.lagPdf(bestilling) shouldBe PdfByggerKlientFeil.left()
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["{}", """{"bytes":null}""", "ugyldig json"])
    fun `ugyldig svar returneres som typet feil`(ugyldigSvar: String) {
        startedWireMockServerWithCorrelationId {
            stubFor(
                WireMock.post(WireMock.urlPathEqualTo(sti))
                    .willReturn(WireMock.okJson(ugyldigSvar)),
            )
            val klient = PdfByggerKlientImpl(baseUrl(), azureAd, clientId)

            klient.lagPdf(bestilling) shouldBe PdfByggerKlientFeil.left()
        }
    }

    private data class PdfResponse(val bytes: ByteArray)
}
