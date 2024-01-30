package no.nav.su.se.bakover.client.skatteetaten

import arrow.core.left
import arrow.core.right
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.http.Body
import com.github.tomakehurst.wiremock.http.Fault
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import no.nav.su.se.bakover.common.SU_SE_BAKOVER_CONSUMER_ID
import no.nav.su.se.bakover.common.auth.AzureAd
import no.nav.su.se.bakover.common.infrastructure.config.ApplicationConfig
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.test.skatt.nySkattegrunnlagForÅr
import no.nav.su.se.bakover.test.wiremock.startedWireMockServerWithCorrelationId
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import vilkår.skatt.domain.KunneIkkeHenteSkattemelding
import vilkår.skatt.domain.SamletSkattegrunnlagForÅr
import vilkår.skatt.domain.SamletSkattegrunnlagForÅrOgStadie
import java.time.LocalDate
import java.time.Year

internal class SkatteClientTest {
    private val azureAdMock = mock<AzureAd> {
        on { onBehalfOfToken(any(), any()) } doReturn "etOnBehalfOfToken"
    }

    fun client(baseUrl: String) =
        SkatteClient(
            skatteetatenConfig = ApplicationConfig.ClientsConfig.SkatteetatenConfig(
                apiBaseUrl = baseUrl,
                clientId = "mocked",
                consumerId = SU_SE_BAKOVER_CONSUMER_ID,
            ),
            azureAd = azureAdMock,
        )

    val fnr = Fnr("21839199217")

    @Test
    fun `nettverks feil håndteres`() {
        startedWireMockServerWithCorrelationId {
            stubFor(
                WireMock.get("/api/v1/spesifisertsummertskattegrunnlag")
                    .willReturn(WireMock.aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)),
            )

            val år = Year.of(2021)
            val expected = SamletSkattegrunnlagForÅr(
                utkast = SamletSkattegrunnlagForÅrOgStadie.Utkast(
                    oppslag = KunneIkkeHenteSkattemelding.Nettverksfeil.left(),
                    inntektsår = år,
                ),
                oppgjør = SamletSkattegrunnlagForÅrOgStadie.Oppgjør(
                    oppslag = KunneIkkeHenteSkattemelding.Nettverksfeil.left(),
                    inntektsår = år,
                ),
                år = år,
            )

            client(baseUrl()).hentSamletSkattegrunnlag(fnr, år).let {
                it.shouldBeInstanceOf<SamletSkattegrunnlagForÅr>()
                it.år shouldBe expected.år
                it.oppgjør.oppslag.shouldBeLeft()
                it.utkast.oppslag.shouldBeLeft()
            }
        }
    }

    @Test
    fun `ukjent fnr returnerer feilkode og tilsvarende skatteoppslagsfeil`() {
        startedWireMockServerWithCorrelationId {
            stubFor(
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
            client(baseUrl()).hentSamletSkattegrunnlag(
                fnr = fnr,
                år = år,
            ) shouldBe SamletSkattegrunnlagForÅr(
                utkast = SamletSkattegrunnlagForÅrOgStadie.Utkast(
                    oppslag = KunneIkkeHenteSkattemelding.FinnesIkke.left(),
                    inntektsår = år,
                ),
                oppgjør = SamletSkattegrunnlagForÅrOgStadie.Oppgjør(
                    oppslag = KunneIkkeHenteSkattemelding.FinnesIkke.left(),
                    inntektsår = år,
                ),
                år = år,
            )
        }
    }

    @Test
    fun `hvis skattegrunnlag ikke eksisterer for fnr og gitt år så mapper vi til tilsvarende skatteoppslagsfeil`() {
        startedWireMockServerWithCorrelationId {
            stubFor(
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
            client(baseUrl()).hentSamletSkattegrunnlag(
                fnr = fnr,
                år = år,
            ) shouldBe SamletSkattegrunnlagForÅr(
                utkast = SamletSkattegrunnlagForÅrOgStadie.Utkast(
                    oppslag = KunneIkkeHenteSkattemelding.FinnesIkke.left(),
                    inntektsår = år,
                ),
                oppgjør = SamletSkattegrunnlagForÅrOgStadie.Oppgjør(
                    oppslag = KunneIkkeHenteSkattemelding.FinnesIkke.left(),
                    inntektsår = år,
                ),
                år = år,
            )
        }
    }

    @Test
    fun `SSG-006 Oppgitt inntektsår er ikke støttet`() {
        startedWireMockServerWithCorrelationId {
            stubFor(
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
                                    "kode": "SSG-006",
                                    "melding": "Oppgitt inntektsår er ikke støttet.",
                                    "korrelasjonsid": "5e22adf0-6144-8ea2-69f0-e68b6b746ce7"
                                  }
                                }
                                    """.trimIndent(),
                                ),
                            ),
                    ),
            )

            val år = Year.of(2021)
            client(baseUrl()).hentSamletSkattegrunnlag(
                fnr = fnr,
                år = år,
            ) shouldBe SamletSkattegrunnlagForÅr(
                utkast = SamletSkattegrunnlagForÅrOgStadie.Utkast(
                    oppslag = KunneIkkeHenteSkattemelding.FinnesIkke.left(),
                    inntektsår = år,
                ),
                oppgjør = SamletSkattegrunnlagForÅrOgStadie.Oppgjør(
                    oppslag = KunneIkkeHenteSkattemelding.FinnesIkke.left(),
                    inntektsår = år,
                ),
                år = år,
            )
        }
    }

    @Test
    fun `feil i mapping håndteres (dato kan ikke parses)`() {
        startedWireMockServerWithCorrelationId {
            stubFor(
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

            client(baseUrl()).hentSamletSkattegrunnlag(
                fnr = fnr,
                år = Year.of(2021),
            ).let {
                it.oppgjør.oppslag
                    .onLeft { it.shouldBeInstanceOf<KunneIkkeHenteSkattemelding.UkjentFeil>() }
                    .onRight { fail("Forventet left") }

                it.utkast.oppslag
                    .onLeft { it.shouldBeInstanceOf<KunneIkkeHenteSkattemelding.UkjentFeil>() }
                    .onRight { fail("Forventet left") }
            }
        }
    }

    @Test
    fun `feil i deserializering håndteres`() {
        startedWireMockServerWithCorrelationId {
            stubFor(
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

            client(baseUrl()).hentSamletSkattegrunnlag(
                fnr = fnr,
                år = Year.of(2021),
            ).let {
                it.oppgjør.oppslag
                    .onLeft { it.shouldBeInstanceOf<KunneIkkeHenteSkattemelding.UkjentFeil>() }
                    .onRight { fail("Forventet left") }

                it.utkast.oppslag
                    .onLeft { it.shouldBeInstanceOf<KunneIkkeHenteSkattemelding.UkjentFeil>() }
                    .onRight { fail("Forventet left") }
            }
        }
    }

    @Test
    fun `success response gir mapped data`() {
        startedWireMockServerWithCorrelationId {
            stubFor(
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
            client(baseUrl()).hentSamletSkattegrunnlag(
                fnr = Fnr(fnr = "04900148157"),
                år = år,
            ) shouldBe SamletSkattegrunnlagForÅr(
                utkast = SamletSkattegrunnlagForÅrOgStadie.Utkast(
                    oppslag = nySkattegrunnlagForÅr(
                        oppgjørsdato = LocalDate.parse("2021-04-01"),
                        verdsettingsrabattSomGirGjeldsreduksjon = emptyList(),
                        oppjusteringAvEierinntekt = emptyList(),
                        manglerKategori = emptyList(),
                        annet = emptyList(),
                    ).right(),
                    inntektsår = år,
                ),
                oppgjør = SamletSkattegrunnlagForÅrOgStadie.Oppgjør(
                    oppslag = nySkattegrunnlagForÅr(
                        oppgjørsdato = LocalDate.parse("2021-04-01"),
                        verdsettingsrabattSomGirGjeldsreduksjon = emptyList(),
                        oppjusteringAvEierinntekt = emptyList(),
                        manglerKategori = emptyList(),
                        annet = emptyList(),
                    ).right(),
                    inntektsår = år,
                ),
                år = år,
            )
        }
    }

    @Test
    fun `kan deserialisere alle feltene i responsen er null`() {
        startedWireMockServerWithCorrelationId {
            stubFor(
                WireMock.get("/api/v1/spesifisertsummertskattegrunnlag")
                    .willReturn(
                        WireMock.ok("""{"grunnlag":null,"svalbardGrunnlag":null,"skatteoppgjoersdato":null}""".trimIndent())
                            .withHeader("Content-Type", "application/json"),
                    ),
            )

            val år = Year.of(2021)
            client(baseUrl()).hentSamletSkattegrunnlag(fnr, år) shouldBe SamletSkattegrunnlagForÅr(
                utkast = SamletSkattegrunnlagForÅrOgStadie.Utkast(
                    oppslag = nySkattegrunnlagForÅr(
                        oppgjørsdato = null,
                        inntekt = emptyList(),
                        formue = emptyList(),
                        formuesFradrag = emptyList(),
                        inntektsfradrag = emptyList(),
                        verdsettingsrabattSomGirGjeldsreduksjon = emptyList(),
                        oppjusteringAvEierinntekt = emptyList(),
                        manglerKategori = emptyList(),
                        annet = emptyList(),
                    ).right(),
                    inntektsår = år,
                ),
                oppgjør = SamletSkattegrunnlagForÅrOgStadie.Oppgjør(
                    oppslag = nySkattegrunnlagForÅr(
                        oppgjørsdato = null,
                        inntekt = emptyList(),
                        formue = emptyList(),
                        formuesFradrag = emptyList(),
                        inntektsfradrag = emptyList(),
                        verdsettingsrabattSomGirGjeldsreduksjon = emptyList(),
                        oppjusteringAvEierinntekt = emptyList(),
                        manglerKategori = emptyList(),
                        annet = emptyList(),
                    ).right(),
                    inntektsår = år,
                ),
                år = år,
            )
        }
    }
}
