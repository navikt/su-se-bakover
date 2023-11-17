package no.nav.su.se.bakover.client.krr

import arrow.core.left
import arrow.core.right
import com.github.tomakehurst.wiremock.client.MappingBuilder
import com.github.tomakehurst.wiremock.client.WireMock
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.client.stubs.azure.AzureClientStub
import no.nav.su.se.bakover.common.infrastructure.config.ApplicationConfig
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.test.wiremock.startedWireMockServerWithCorrelationId
import org.junit.jupiter.api.Test

class KontaktOgReservasjonsregisterClientTest {

    private val fødselsnummer = "10109900100"

    private fun client(baseUrl: String) = KontaktOgReservasjonsregisterClient(

        config = ApplicationConfig.ClientsConfig.KontaktOgReservasjonsregisterConfig(
            appId = "appId",
            url = baseUrl,
        ),
        azure = AzureClientStub,
    )

    private val fnr: Fnr = Fnr(fødselsnummer)

    @Test
    fun `parser respons`() {
        startedWireMockServerWithCorrelationId {
            stubFor(
                wiremockBuilder
                    .willReturn(
                        WireMock.okJson(
                            """
                        {
                          "personident": "$fødselsnummer",
                          "aktiv": true,
                          "kanVarsles": false,
                          "reservert": false,
                          "epostadresse": "noreply@nav.no",
                          "mobiltelefonnummer": "11111111",
                          "spraak": "nb"
                        }
                            """.trimIndent(),
                        ),
                    ),
            )
            client(baseUrl()).hentKontaktinformasjon(fnr) shouldBe Kontaktinformasjon(
                epostadresse = "noreply@nav.no",
                mobiltelefonnummer = "11111111",
                reservert = false,
                kanVarsles = false,
                språk = "nb",
            ).right()
        }
    }

    @Test
    fun `ingen informasjon registrert`() {
        startedWireMockServerWithCorrelationId {
            stubFor(
                wiremockBuilder
                    .willReturn(
                        WireMock.okJson(
                            """
                        {
                          "personident": "$fødselsnummer",
                          "aktiv": false                        
                        }
                            """.trimIndent(),
                        ),
                    ),
            )
            client(baseUrl()).hentKontaktinformasjon(fnr) shouldBe KontaktOgReservasjonsregister.KunneIkkeHenteKontaktinformasjon.BrukerErIkkeRegistrert.left()
        }
    }

    @Test
    fun `svarer med feil dersom respons fra krr indikerer feil`() {
        startedWireMockServerWithCorrelationId {
            stubFor(wiremockBuilder.willReturn(WireMock.notFound()))
            client(baseUrl()).hentKontaktinformasjon(fnr) shouldBe KontaktOgReservasjonsregister.KunneIkkeHenteKontaktinformasjon.FeilVedHenting.left()
        }
    }

    private val wiremockBuilder: MappingBuilder = WireMock.get(WireMock.urlPathEqualTo(PERSON_PATH))
        .withHeader("Authorization", WireMock.equalTo("Bearer token"))
        .withHeader("Accept", WireMock.equalTo("application/json"))
        .withHeader("Nav-Call-Id", WireMock.equalTo("correlationId"))
        .withHeader("Nav-Personident", WireMock.equalTo("10109900100"))
}
