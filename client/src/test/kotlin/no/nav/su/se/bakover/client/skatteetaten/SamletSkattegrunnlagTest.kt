package no.nav.su.se.bakover.client.skatteetaten

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.http.Body
import com.github.tomakehurst.wiremock.http.Fault
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import no.nav.su.se.bakover.client.WiremockBase.Companion.wireMockServer
import no.nav.su.se.bakover.common.ApplicationConfig
import no.nav.su.se.bakover.common.Fnr
import no.nav.su.se.bakover.common.auth.AzureAd
import no.nav.su.se.bakover.common.suSeBakoverConsumerId
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.skatt.skattegrunnlag
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.slf4j.MDC
import java.time.Year

internal class SamletSkattegrunnlagTest {
    private val azureAdMock = mock<AzureAd> {
        on { onBehalfOfToken(any(), any()) } doReturn "etOnBehalfOfToken"
    }
    val client =
        SkatteClient(
            skatteetatenConfig = ApplicationConfig.ClientsConfig.SkatteetatenConfig(
                apiBaseUrl = wireMockServer.baseUrl(),
                consumerId = suSeBakoverConsumerId,
            ),
            fixedClock,
            azureAd = azureAdMock,
        )
    val fnr = Fnr("21839199217")

    @Test
    fun `nettverks feil håndteres`() {
        wireMockServer.stubFor(
            WireMock.get(wireMockServer.baseUrl())
                .willReturn(WireMock.aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)),
        )
        client.hentSamletSkattegrunnlag(fnr, Year.of(2021))
            .shouldBeInstanceOf<Either.Left<SkatteoppslagFeil.Nettverksfeil>>()
    }

    @Test
    fun `ukjent fnr returnerer feilkode og tilsvarende skatteoppslagsfeil`() {
        wireMockServer.stubFor(
            WireMock.get("/api/spesifisertsummertskattegrunnlag")
                .willReturn(
                    WireMock.aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withStatus(404)
                        .withResponseBody(
                            Body(
                                """
                                {
                                  "kode": "SSG-007",
                                  "melding": "Ikke treff på oppgitt personidentifikator",
                                  "korrelasjonsid": "23a235f5-28f9-47db-9abd-ab78977c32fa"
                                }
                                """.trimIndent(),
                            ),
                        ),
                ),
        )

        client.hentSamletSkattegrunnlag(
            fnr = fnr,
            inntektsÅr = Year.of(2021),
        ) shouldBe SkatteoppslagFeil.FantIkkeSkattegrunnlagForPersonOgÅr.left()
    }

    @Test
    fun `hvis skattegrunnlag ikke eksisterer for fnr og gitt år så mapper vi til tilsvarende skatteoppslagsfeil`() {
        wireMockServer.stubFor(
            WireMock.get("/api/spesifisertsummertskattegrunnlag")
                .willReturn(
                    WireMock.aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withStatus(404)
                        .withResponseBody(
                            Body(
                                """
                                {
                                  "kode": "SSG-008",
                                  "melding": "Ingen summert skattegrunnlag funnet på oppgitt personidentifikator og inntektsår",
                                  "korrelasjonsid": "fc0f8e22-ebd7-11ec-8ea0-0242ac120002"
                                }
                                """.trimIndent(),
                            ),
                        ),
                ),
        )

        client.hentSamletSkattegrunnlag(
            fnr = fnr,
            inntektsÅr = Year.of(2021),
        ) shouldBe SkatteoppslagFeil.FantIkkeSkattegrunnlagForPersonOgÅr.left()
    }

    @Test
    fun `feil i deserializering håndteres`() {
        val feilFormatPåBody = """
            {
             "personidentifikator": "$fnr",
             "ggruunnllaagg": []
            }
        """.trimIndent()
        wireMockServer.stubFor(
            WireMock.get("/api/spesifisertsummertskattegrunnlag")
                .willReturn(
                    WireMock.ok(feilFormatPåBody)
                        .withHeader("Content-Type", "application/json"),
                ),
        )

        val response = client.hentSamletSkattegrunnlag(
            fnr = fnr,
            inntektsÅr = Year.of(2021),
        )

        response.shouldBeInstanceOf<Either.Left<SkatteoppslagFeil.UkjentFeil>>()
    }

    @Test
    fun `feil i data-mappingen håndteres`() {
        val feilformatertFnr = "123 x 567 y"
        wireMockServer.stubFor(
            WireMock.get("/api/spesifisertsummertskattegrunnlag")
                .willReturn(
                    WireMock.ok(
                        """
                        {
                          "personidentifikator": "$feilformatertFnr",
                          "inntektsaar": "2021",
                          "skjermet": false,
                          "grunnlag": [],
                          "skatteoppgjoersdato": "2022-02-10"
                        }
                        """.trimIndent(),
                    )
                        .withHeader("Content-Type", "application/json"),
                ),
        )

        client.hentSamletSkattegrunnlag(
            fnr = fnr,
            inntektsÅr = Year.of(2021),
        ) shouldBe SkatteoppslagFeil.UkjentFeil(IllegalArgumentException("")).left()
    }

    @Test
    fun `success response gir mapped data`() {
        wireMockServer.stubFor(
            WireMock.get("/api/spesifisertsummertskattegrunnlag")
                .willReturn(
                    // language=JSON
                    WireMock.ok(
                        """
                        {
                          "grunnlag": [
                            {
                              "beloep": "1000",
                              "tekniskNavn": "alminneligInntektFoerSaerfradrag",
                              "kategori": ["inntekt"]
                            },
                            {
                              "beloep": "6000",
                              "tekniskNavn": "samletAnnenGjeld",
                              "kategori": ["formuesfradrag"]
                            },
                            {
                              "beloep": "4000",
                              "tekniskNavn": "fradragForFagforeningskontingent",
                              "kategori": ["inntektsfradrag"]
                            }
                          ],
                          "skatteoppgjoersdato": "2021-04-01",
                          "svalbardGrunnlag": [
                            {
                              "beloep": "20000",
                              "tekniskNavn": "formuesverdiForKjoeretoey",
                              "kategori": ["formue"],
                              "spesifisering": [
                                {
                                  "type": "Kjoeretoey",
                                  "aarForFoerstegangsregistrering": "1957",
                                  "beloep": "15000",
                                  "fabrikatnavn": "Troll",
                                  "formuesverdi": "15000",
                                  "registreringsnummer": "AB12345"
                                },
                                  {
                                  "type": "Kjoeretoey",
                                  "aarForFoerstegangsregistrering": "2003",
                                  "antattMarkedsverdi": null,
                                  "antattVerdiSomNytt": null,
                                  "beloep": "5000",
                                  "fabrikatnavn": "Think",
                                  "formuesverdi": "5000",
                                  "registreringsnummer": "BC67890"
                                }
                              ]
                            }
                          ]
                        }
                        """.trimIndent(),
                    )
                        .withHeader("Content-Type", "application/json"),
                ),
        )

        client.hentSamletSkattegrunnlag(
            fnr = fnr,
            inntektsÅr = Year.of(2021),
        ) shouldBe skattegrunnlag().right()
    }

    @BeforeEach
    fun beforeEach() {
        MDC.put("Authorization", "Bearer abc")
    }
}
