package tilbakekreving.infrastructure.client

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.matching
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.auth.AzureAd
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.domain.tid.januar
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.infrastructure.config.ApplicationConfig.ClientsConfig.SuProxyConfig
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.test.TikkendeKlokke
import no.nav.su.se.bakover.test.fixedClockAt
import no.nav.su.se.bakover.test.kravgrunnlag.kravgrunnlag
import no.nav.su.se.bakover.test.vurderingerMedKrav
import no.nav.su.se.bakover.test.wiremock.startedWireMockServerWithCorrelationId
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.times

class TilbakekrevingProxyClientTest {
    private fun mockAzureAd() = mock<AzureAd> {
        on { onBehalfOfToken(any(), any()) } doReturn "obo-token"
    }
    val clientId = "proxyclient"
    private fun createClient(baseUrl: String, azureAd: AzureAd = mockAzureAd()): TilbakekrevingProxyClient {
        val config = SuProxyConfig(url = baseUrl, clientId = clientId)
        val clock = TikkendeKlokke(fixedClockAt(1.januar(2022)))
        return TilbakekrevingProxyClient(
            azureAd = azureAd,
            config = config,
            clock = clock,
        )
    }

    private fun validSoapResponse(): String =
        """
    <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/">
      <soapenv:Body>
        <tilbakekrevingsvedtakResponse>
          <resultat>OK</resultat>
        </tilbakekrevingsvedtakResponse>
      </soapenv:Body>
    </soapenv:Envelope>
        """.trimIndent()

    @Test
    fun `bruker OBO-token og ikke systemtoken for send vedtak tk - happy case`() {
        val brukertoken = "usertoken"

        startedWireMockServerWithCorrelationId(token = brukertoken) {
            stubFor(
                post(urlEqualTo("/tilbakekreving/vedtak"))
                    .withHeader("Authorization", matching("Bearer .*"))
                    .willReturn(
                        aResponse()
                            .withStatus(200)
                            .withBody(validSoapResponse()),
                    ),
            )

            val azureAd = mockAzureAd()
            val client = createClient(baseUrl(), azureAd)

            val result = client.sendTilbakekrevingsvedtak(
                vurderingerMedKrav = vurderingerMedKrav(),
                attestertAv = NavIdentBruker.Attestant("A12323"),
                sakstype = Sakstype.UFØRE,
            ).shouldBeRight()

            result.responseXml shouldBe validSoapResponse()
            org.mockito.kotlin.verify(azureAd, times(1)).onBehalfOfToken(brukertoken, clientId)
            org.mockito.kotlin.verify(azureAd, times(0)).getSystemToken(clientId)
        }
    }

    @Test
    fun `Klarer å mappe 500 feil send `() {
        val brukertoken = "usertoken"
        val feil500status = TilbakekrevingErrorDto(TilbakekrevingErrorCode.FeilStatusFraOppdrag)
        startedWireMockServerWithCorrelationId(token = brukertoken) {
            stubFor(
                post(urlEqualTo("/tilbakekreving/vedtak"))
                    .withHeader("Authorization", matching("Bearer .*"))
                    .willReturn(
                        aResponse()
                            .withStatus(500)
                            .withBody(serialize(feil500status)),
                    ),
            )

            val azureAd = mockAzureAd()
            val client = createClient(baseUrl(), azureAd)

            val answer = mapErrorSend(feil500status.code)
            client.sendTilbakekrevingsvedtak(
                vurderingerMedKrav = vurderingerMedKrav(),
                attestertAv = NavIdentBruker.Attestant("A12323"),
                sakstype = Sakstype.UFØRE,
            ).shouldBeLeft(answer)

            org.mockito.kotlin.verify(azureAd, times(1)).onBehalfOfToken(brukertoken, clientId)
            org.mockito.kotlin.verify(azureAd, times(0)).getSystemToken(clientId)
        }
    }

    /* Annuller */
    @Test
    fun `bruker OBO-token og ikke systemtoken for annuller kravgrunnlag - happy case`() {
        val brukertoken = "usertoken"

        startedWireMockServerWithCorrelationId(token = brukertoken) {
            stubFor(
                post(urlEqualTo("/tilbakekreving/annuller"))
                    .withHeader("Authorization", matching("Bearer .*"))
                    .willReturn(
                        aResponse()
                            .withStatus(200)
                            .withBody(validSoapResponse()),
                    ),
            )

            val azureAd = mockAzureAd()
            val client = createClient(baseUrl(), azureAd)

            val result = client.annullerKravgrunnlag(
                annullertAv = NavIdentBruker.Saksbehandler("Z123"),
                kravgrunnlagSomSkalAnnulleres = kravgrunnlag(),
            ).shouldBeRight()

            result.responseXml shouldBe validSoapResponse()

            org.mockito.kotlin.verify(azureAd, times(1)).onBehalfOfToken(brukertoken, clientId)
            org.mockito.kotlin.verify(azureAd, times(0)).getSystemToken(any())
        }
    }

    @Test
    fun `Klarer å mappe 500 feil annuller `() {
        val brukertoken = "usertoken"
        val feil500status = TilbakekrevingErrorDto(TilbakekrevingErrorCode.FeilStatusFraOppdrag)
        startedWireMockServerWithCorrelationId(token = brukertoken) {
            stubFor(
                post(urlEqualTo("/tilbakekreving/annuller"))
                    .withHeader("Authorization", matching("Bearer .*"))
                    .willReturn(
                        aResponse()
                            .withStatus(500)
                            .withBody(serialize(feil500status)),
                    ),
            )

            val azureAd = mockAzureAd()
            val client = createClient(baseUrl(), azureAd)

            val answer = mapErrorAnnuller(feil500status.code)
            client.annullerKravgrunnlag(
                annullertAv = NavIdentBruker.Saksbehandler("Z123"),
                kravgrunnlagSomSkalAnnulleres = kravgrunnlag(),
            ).shouldBeLeft(answer)

            org.mockito.kotlin.verify(azureAd, times(1)).onBehalfOfToken(brukertoken, clientId)
            org.mockito.kotlin.verify(azureAd, times(0)).getSystemToken(clientId)
        }
    }
}
