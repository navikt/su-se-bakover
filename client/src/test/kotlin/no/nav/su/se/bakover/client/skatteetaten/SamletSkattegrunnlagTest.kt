package no.nav.su.se.bakover.client.skatteetaten

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.http.Body
import com.github.tomakehurst.wiremock.http.Fault
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import no.nav.su.se.bakover.client.WiremockBase.Companion.wireMockServer
import no.nav.su.se.bakover.common.ApplicationConfig
import no.nav.su.se.bakover.common.Fnr
import no.nav.su.se.bakover.common.auth.AzureAd
import no.nav.su.se.bakover.common.suSeBakoverConsumerId
import no.nav.su.se.bakover.domain.person.PersonOppslag
import no.nav.su.se.bakover.domain.skatt.SamletSkattegrunnlagResponseMedStadie
import no.nav.su.se.bakover.domain.skatt.SamletSkattegrunnlagResponseMedYear
import no.nav.su.se.bakover.domain.skatt.SkatteoppslagFeil
import no.nav.su.se.bakover.domain.skatt.Stadie
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.person
import no.nav.su.se.bakover.test.skatt.nyÅrsgrunnlag
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.slf4j.MDC
import java.io.IOException
import java.time.Year
import java.time.format.DateTimeParseException

internal class SamletSkattegrunnlagTest {
    private val azureAdMock = mock<AzureAd> {
        on { onBehalfOfToken(any(), any()) } doReturn "etOnBehalfOfToken"
    }

    private val personClientMock = mock<PersonOppslag> {
        on { this.person(any()) } doReturn person().right()
        on { this.sjekkTilgangTilPerson(any()) } doReturn Unit.right()
    }

    val client =
        SkatteClient(
            personOppslag = personClientMock,
            skatteetatenConfig = ApplicationConfig.ClientsConfig.SkatteetatenConfig(
                apiBaseUrl = wireMockServer.baseUrl(),
                clientId = "mocked",
                consumerId = suSeBakoverConsumerId,
            ),
            azureAd = azureAdMock,
        )
    val fnr = Fnr("21839199217")

    @Test
    fun `nettverks feil håndteres`() {
        wireMockServer.stubFor(
            WireMock.get("/api/v1/spesifisertsummertskattegrunnlag")
                .willReturn(WireMock.aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)),
        )

        val nettverksfeil = SkatteoppslagFeil.Nettverksfeil(IOException("Connection reset"))
        val år = Year.of(2021)
        val expected = SamletSkattegrunnlagResponseMedYear(
            skatteResponser = listOf(
                SamletSkattegrunnlagResponseMedStadie(oppslag = nettverksfeil.left(), stadie = Stadie.OPPGJØR, år),
                SamletSkattegrunnlagResponseMedStadie(oppslag = nettverksfeil.left(), stadie = Stadie.UTKAST, år),
            ),
            år = år,
        )

        client.hentSamletSkattegrunnlag(fnr, år).let {
            it.shouldBeInstanceOf<Either.Right<SamletSkattegrunnlagResponseMedYear>>()
            it.value.år shouldBe expected.år
            it.value.skatteResponser[0].stadie shouldBe Stadie.OPPGJØR
            it.value.skatteResponser[0].oppslag.shouldBeLeft()
            it.value.skatteResponser.last().stadie shouldBe Stadie.UTKAST
            it.value.skatteResponser.last().oppslag.shouldBeLeft()
        }
    }

    @Test
    fun `ukjent fnr returnerer feilkode og tilsvarende skatteoppslagsfeil`() {
        wireMockServer.stubFor(
            WireMock.get("/api/v1/spesifisertsummertskattegrunnlag")
                .willReturn(
                    WireMock.aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withStatus(404)
                        .withResponseBody(
                            Body(
                                """
                                {
                                  "ske-message": {
                                    "kode": "SSG-007",
                                    "melding": "Ikke treff på oppgitt personidentifikator",
                                    "korrelasjonsid": "23a235f5-28f9-47db-9abd-ab78977c32fa"
                                  }
                                }
                                """.trimIndent(),
                            ),
                        ),
                ),
        )

        val år = Year.of(2021)
        client.hentSamletSkattegrunnlag(
            fnr = fnr,
            år = år,
        ) shouldBe SamletSkattegrunnlagResponseMedYear(
            skatteResponser = listOf(
                SamletSkattegrunnlagResponseMedStadie(
                    oppslag = SkatteoppslagFeil.FantIkkeSkattegrunnlagForPersonOgÅr(år).left(),
                    stadie = Stadie.OPPGJØR,
                    inntektsår = år,
                ),
                SamletSkattegrunnlagResponseMedStadie(
                    oppslag = SkatteoppslagFeil.FantIkkeSkattegrunnlagForPersonOgÅr(år).left(),
                    stadie = Stadie.UTKAST,
                    inntektsår = år,
                ),
            ),
            år = år,
        ).right()
    }

    @Test
    fun `hvis skattegrunnlag ikke eksisterer for fnr og gitt år så mapper vi til tilsvarende skatteoppslagsfeil`() {
        wireMockServer.stubFor(
            WireMock.get("/api/v1/spesifisertsummertskattegrunnlag")
                .willReturn(
                    WireMock.aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withStatus(404)
                        .withResponseBody(
                            Body(
                                """
                                {
                                  "ske-message": {
                                    "kode": "SSG-008",
                                    "melding": "Ingen summert skattegrunnlag funnet på oppgitt personidentifikator og inntektsår",
                                    "korrelasjonsid": "fc0f8e22-ebd7-11ec-8ea0-0242ac120002"
                                  }
                                }
                                """.trimIndent(),
                            ),
                        ),
                ),
        )

        val år = Year.of(2021)
        client.hentSamletSkattegrunnlag(
            fnr = fnr,
            år = år,
        ) shouldBe SamletSkattegrunnlagResponseMedYear(
            skatteResponser = listOf(
                SamletSkattegrunnlagResponseMedStadie(
                    oppslag = SkatteoppslagFeil.FantIkkeSkattegrunnlagForPersonOgÅr(år).left(),
                    stadie = Stadie.OPPGJØR,
                    inntektsår = år,
                ),
                SamletSkattegrunnlagResponseMedStadie(
                    oppslag = SkatteoppslagFeil.FantIkkeSkattegrunnlagForPersonOgÅr(år).left(),
                    stadie = Stadie.UTKAST,
                    inntektsår = år,
                ),
            ),
            år = år,
        ).right()
    }

    @Test
    fun `feil i mapping håndteres`() {
        wireMockServer.stubFor(
            WireMock.get("/api/v1/spesifisertsummertskattegrunnlag")
                .willReturn(
                    WireMock.ok(
                        """
                        {
                         "skatteoppgjoersdato":"en-dato-som-ikke-kan-parses"
                        }
                        """.trimIndent(),
                    )
                        .withHeader("Content-Type", "application/json"),
                ),
        )

        client.hentSamletSkattegrunnlag(
            fnr = fnr,
            år = Year.of(2021),
        ).getOrFail().skatteResponser.first().oppslag.onLeft {
            it.shouldBeInstanceOf<SkatteoppslagFeil.UkjentFeil>()
            it.throwable.shouldBeInstanceOf<DateTimeParseException>()
        }.onRight { fail("Forventet left") }
    }

    @Test
    fun `feil i deserializering håndteres`() {
        wireMockServer.stubFor(
            WireMock.get("/api/v1/spesifisertsummertskattegrunnlag")
                .willReturn(
                    WireMock.ok(
                        """
                        {
                         "grunnlag":[{}]
                        }
                        """.trimIndent(),
                    )
                        .withHeader("Content-Type", "application/json"),
                ),
        )

        client.hentSamletSkattegrunnlag(
            fnr = fnr,
            år = Year.of(2021),
        ).getOrFail().skatteResponser.first().oppslag.onLeft {
            it.shouldBeInstanceOf<SkatteoppslagFeil.UkjentFeil>()
            // Denne vil endre seg i en senere jackson versjon fra com.fasterxml.jackson.module.kotlin.MissingKotlinParameterException til com.fasterxml.jackson.databind.exc.MismatchedInputException
            it.throwable::class.simpleName shouldBe "MissingKotlinParameterException"
            it.throwable.message shouldContain "non-nullable type"
        }.onRight { fail("Forventet left") }
    }

    @Test
    fun `success response gir mapped data`() {
        wireMockServer.stubFor(
            WireMock.get("/api/v1/spesifisertsummertskattegrunnlag")
                .willReturn(
                    // language=JSON
                    WireMock.ok(
                        """
                        {
                          "grunnlag": [
                          {
                            "kategori": "formue",
                            "tekniskNavn": "bruttoformue",
                            "beloep": "1238"
                          },
                            {
                              "beloep": "1000",
                              "tekniskNavn": "alminneligInntektFoerSaerfradrag",
                              "kategori": "inntekt"
                            },
                            {
                              "beloep": "6000",
                              "tekniskNavn": "samletAnnenGjeld",
                              "kategori": "formuesfradrag"
                            },
                            {
                              "beloep": "4000",
                              "tekniskNavn": "fradragForFagforeningskontingent",
                              "kategori": "inntektsfradrag"
                            }
                          ],
                          "skatteoppgjoersdato": "2021-04-01",
                          "svalbardGrunnlag": [
                            {
                              "beloep": "20000",
                              "tekniskNavn": "formuesverdiForKjoeretoey",
                              "kategori": "formue",
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

        val år = Year.of(2021)
        client.hentSamletSkattegrunnlag(
            fnr = Fnr(fnr = "04900148157"),
            år = år,
        ) shouldBe SamletSkattegrunnlagResponseMedYear(
            skatteResponser = listOf(
                SamletSkattegrunnlagResponseMedStadie(
                    oppslag = nyÅrsgrunnlag(stadie = Stadie.OPPGJØR).right(),
                    stadie = Stadie.OPPGJØR,
                    inntektsår = år,
                ),
                SamletSkattegrunnlagResponseMedStadie(
                    oppslag = nyÅrsgrunnlag(stadie = Stadie.UTKAST).right(),
                    stadie = Stadie.UTKAST,
                    inntektsår = år,
                ),
            ),
            år = år,
        ).right()
    }

    companion object {
        @JvmStatic
        @BeforeAll
        fun beforeAll() {
            MDC.put("Authorization", "Bearer abc")
        }
    }
}
