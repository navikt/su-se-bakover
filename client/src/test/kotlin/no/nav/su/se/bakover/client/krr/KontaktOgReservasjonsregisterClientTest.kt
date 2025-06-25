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
    fun `parser respons ny`() {
        startedWireMockServerWithCorrelationId {
            stubFor(
                wiremockBuilder
                    .willReturn(
                        WireMock.okJson(
                            """
                            {
                              "personer": {
                                "$fødselsnummer": 
                                  {
                                      "personident": "$fødselsnummer",
                                      "aktiv": true,
                                      "kanVarsles": false,
                                      "reservert": false,
                                      "epostadresse": "noreply@nav.no",
                                      "mobiltelefonnummer": "11111111",
                                      "spraak": "nb"
                                 }    
                              },
                              "feil": {}
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
    fun `svarer med feil dersom respons fra krr indikerer feil ny`() {
        startedWireMockServerWithCorrelationId {
            stubFor(
                wiremockBuilder.willReturn(
                    WireMock.okJson(
                        """
                            {
                              "feil": {
                                "$fødselsnummer": "person_ikke_funnet"
                              }
                            }
                        """.trimIndent(),
                    ),
                ),
            )
            client(baseUrl()).hentKontaktinformasjon(fnr) shouldBe KontaktOgReservasjonsregister.KunneIkkeHenteKontaktinformasjon.FeilVedHenting.left()
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
                              "personer": {
                                "$fødselsnummer": 
                                  {
                                      "personident": "$fødselsnummer",
                                      "aktiv": false
                                 }    
                              },
                              "feil": {}
                            }
                            """.trimIndent(),
                        ),
                    ),
            )
            client(baseUrl()).hentKontaktinformasjon(fnr) shouldBe KontaktOgReservasjonsregister.KunneIkkeHenteKontaktinformasjon.BrukerErIkkeRegistrert.left()
        }
    }

    private val wiremockBuilder: MappingBuilder = WireMock.post(WireMock.urlPathEqualTo(PERSONER_PATH))
        .withHeader("Authorization", WireMock.equalTo("Bearer token"))
        .withHeader("Accept", WireMock.equalTo("application/json"))
        .withHeader("Nav-Call-Id", WireMock.equalTo("correlationId"))
        .withRequestBody(
            WireMock.containing(
                """"personidenter":["10109900100"]""".trimIndent(),
            ),
        )
}
