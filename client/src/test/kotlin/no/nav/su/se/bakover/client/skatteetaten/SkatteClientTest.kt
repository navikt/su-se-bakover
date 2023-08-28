package no.nav.su.se.bakover.client.skatteetaten

import arrow.core.left
import arrow.core.right
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.http.Body
import com.github.tomakehurst.wiremock.http.Fault
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import no.nav.su.se.bakover.client.WiremockBase.Companion.wireMockServer
import no.nav.su.se.bakover.common.auth.AzureAd
import no.nav.su.se.bakover.common.infrastructure.config.ApplicationConfig
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.suSeBakoverConsumerId
import no.nav.su.se.bakover.domain.person.PersonOppslag
import no.nav.su.se.bakover.domain.skatt.KunneIkkeHenteSkattemelding
import no.nav.su.se.bakover.domain.skatt.SamletSkattegrunnlagForÅr
import no.nav.su.se.bakover.domain.skatt.SamletSkattegrunnlagForÅrOgStadie
import no.nav.su.se.bakover.test.person
import no.nav.su.se.bakover.test.skatt.nySkattegrunnlagForÅr
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.slf4j.MDC
import java.time.LocalDate
import java.time.Year

internal class SkatteClientTest {
    private val azureAdMock = mock<AzureAd> {
        on { onBehalfOfToken(any(), any()) } doReturn "etOnBehalfOfToken"
    }

    private val personClientMock = mock<PersonOppslag> {
        on { this.person(any()) } doReturn person().right()
        on { this.sjekkTilgangTilPerson(any()) } doReturn Unit.right()
    }

    val client =
        SkatteClient(
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

        client.hentSamletSkattegrunnlag(fnr, år).let {
            it.shouldBeInstanceOf<SamletSkattegrunnlagForÅr>()
            it.år shouldBe expected.år
            it.oppgjør.oppslag.shouldBeLeft()
            it.utkast.oppslag.shouldBeLeft()
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

    @Test
    fun `feil i mapping håndteres (dato kan ikke parses)`() {
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
        ).let {
            it.oppgjør.oppslag
                .onLeft { it.shouldBeInstanceOf<KunneIkkeHenteSkattemelding.UkjentFeil>() }
                .onRight { fail("Forventet left") }

            it.utkast.oppslag
                .onLeft { it.shouldBeInstanceOf<KunneIkkeHenteSkattemelding.UkjentFeil>() }
                .onRight { fail("Forventet left") }
        }
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
        ).let {
            it.oppgjør.oppslag
                .onLeft { it.shouldBeInstanceOf<KunneIkkeHenteSkattemelding.UkjentFeil>() }
                .onRight { fail("Forventet left") }

            it.utkast.oppslag
                .onLeft { it.shouldBeInstanceOf<KunneIkkeHenteSkattemelding.UkjentFeil>() }
                .onRight { fail("Forventet left") }
        }
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

    @Test
    fun `kan deserialisere alle feltene i responsen er null`() {
        wireMockServer.stubFor(
            WireMock.get("/api/v1/spesifisertsummertskattegrunnlag")
                .willReturn(
                    WireMock.ok("""{"grunnlag":null,"svalbardGrunnlag":null,"skatteoppgjoersdato":null}""".trimIndent())
                        .withHeader("Content-Type", "application/json"),
                ),
        )

        val år = Year.of(2021)
        client.hentSamletSkattegrunnlag(fnr, år) shouldBe SamletSkattegrunnlagForÅr(
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

    companion object {
        @JvmStatic
        @BeforeAll
        fun beforeAll() {
            MDC.put("Authorization", "Bearer abc")
        }
    }
}
