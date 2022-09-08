package no.nav.su.se.bakover.client.krr

import arrow.core.left
import arrow.core.right
import com.github.tomakehurst.wiremock.client.MappingBuilder
import com.github.tomakehurst.wiremock.client.WireMock
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.client.WiremockBase
import no.nav.su.se.bakover.client.stubs.azure.AzureClientStub
import no.nav.su.se.bakover.common.ApplicationConfig
import no.nav.su.se.bakover.domain.Fnr
import org.junit.jupiter.api.Test

class KontaktOgReservasjonsregisterClientTest : WiremockBase {
    private val fødselsnummer = "10109900100"

    private val client = KontaktOgReservasjonsregisterClient(
        config = ApplicationConfig.ClientsConfig.KontaktOgReservasjonsregisterConfig(
            appId = "appId",
            url = WiremockBase.wireMockServer.baseUrl()
        ),
        azure = AzureClientStub
    )
    private val fnr: Fnr = Fnr(fødselsnummer)

    @Test
    fun `parser respons`() {
        WiremockBase.wireMockServer.stubFor(
            wiremockBuilder
                .willReturn(
                    WireMock.okJson(
                        """
                        {
                          "personident": "$fødselsnummer",
                          "kanVarsles": false,
                          "reservert": false,
                          "epostadresse": "noreply@nav.no",
                          "mobiltelefonnummer": "11111111",
                          "spraak": "nb"
                        }
                        """.trimIndent()
                    )
                )
        )
        client.hentKontaktinformasjon(fnr) shouldBe Kontaktinformasjon(
            epostadresse = "noreply@nav.no",
            mobiltelefonnummer = "11111111",
            reservert = false,
            kanVarsles = false,
            språk = "nb"
        ).right()
    }

    @Test
    fun `svarer med feil dersom respons fra krr indikerer feil`() {
        WiremockBase.wireMockServer.stubFor(wiremockBuilder.willReturn(WireMock.notFound()))
        client.hentKontaktinformasjon(fnr) shouldBe KontaktOgReservasjonsregister.KunneIkkeHenteKontaktinformasjon.left()
    }

    private val wiremockBuilder: MappingBuilder = WireMock.get(WireMock.urlPathEqualTo(personPath))
        .withHeader("Authorization", WireMock.equalTo("Bearer etFintSystemtoken"))
        .withHeader("Accept", WireMock.equalTo("application/json"))
        .withHeader("Nav-Call-Id", WireMock.equalTo("correlationId"))
}
