package no.nav.su.se.bakover.client.dkif

import arrow.core.left
import arrow.core.right
import com.github.tomakehurst.wiremock.client.MappingBuilder
import com.github.tomakehurst.wiremock.client.WireMock
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.client.WiremockBase
import no.nav.su.se.bakover.client.stubs.sts.TokenOppslagStub
import no.nav.su.se.bakover.domain.person.Fnr
import org.junit.jupiter.api.Test

class DkifClientTest : WiremockBase {
    private val fødselsnummer = "10109900100"
    private val consumerId = "consumerId"

    private val client = DkifClient(WiremockBase.wireMockServer.baseUrl(), TokenOppslagStub, consumerId)
    private val fnr: Fnr = Fnr(fødselsnummer)

    @Test
    fun `should parse kontaktinfo correctly`() {
        WiremockBase.wireMockServer.stubFor(
            wiremockBuilder
                .willReturn(
                    WireMock.okJson(
                        """
                        {
                          "kontaktinfo": {
                            "$fødselsnummer": {
                              "personident": "$fødselsnummer",
                              "kanVarsles": false,
                              "reservert": false,
                              "epostadresse": "noreply@nav.no",
                              "mobiltelefonnummer": "11111111",
                              "spraak": "nb"
                            }
                          },
                          "feil": {}
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
    fun `should return error if response contains feil`() {
        WiremockBase.wireMockServer.stubFor(
            wiremockBuilder
                .willReturn(
                    WireMock.okJson(
                        """
                        {
                          "feil": {
                            "$fødselsnummer": {
                              "melding": "Et eller annet gikk galt"
                            }
                          },
                          "kontaktinfo": {}
                        }
                        """.trimIndent()
                    )
                )
        )
        client.hentKontaktinformasjon(fnr) shouldBe DigitalKontaktinformasjon.KunneIkkeHenteKontaktinformasjon.left()
    }

    private val wiremockBuilder: MappingBuilder = WireMock.get(WireMock.urlPathEqualTo(dkifPath))
        .withHeader("Authorization", WireMock.equalTo("Bearer token"))
        .withHeader("Accept", WireMock.equalTo("application/json"))
        .withHeader("Nav-Consumer-Id", WireMock.equalTo(consumerId))
        .withHeader("Nav-Call-Id", WireMock.equalTo("correlationId"))
        .withHeader("Nav-Personidenter", WireMock.equalTo(fødselsnummer))
}
