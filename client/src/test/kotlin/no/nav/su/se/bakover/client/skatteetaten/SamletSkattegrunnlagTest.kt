package no.nav.su.se.bakover.client.skatteetaten

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.http.Body
import com.github.tomakehurst.wiremock.http.Fault
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import no.nav.su.se.bakover.client.AccessToken
import no.nav.su.se.bakover.client.WiremockBase.Companion.wireMockServer
import no.nav.su.se.bakover.common.ApplicationConfig
import no.nav.su.se.bakover.common.Fnr
import no.nav.su.se.bakover.domain.skatt.Skattegrunnlag
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedTidspunkt
import org.junit.jupiter.api.Test
import java.time.LocalDate

internal class SamletSkattegrunnlagTest {
    val client =
        SkatteClient(skatteetatenConfig = ApplicationConfig.ClientsConfig.SkatteetatenConfig(apiUri = wireMockServer.baseUrl()), fixedClock)
    val fnr = Fnr("21839199217")

    @Test
    fun `nettverks feil håndteres`() {
        wireMockServer.stubFor(WireMock.get(wireMockServer.baseUrl()).willReturn(WireMock.aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)))
        client.hentSamletSkattegrunnlag(AccessToken("mockedJWT"), fnr).shouldBeInstanceOf<Either.Left<SkatteoppslagFeil.Nettverksfeil>>()
    }

    @Test
    fun `ukjent fnr returnerer feilkode og tilsvarende skatteoppslagsfeil`() {
        wireMockServer.stubFor(
            WireMock.get("/api/formueinntekt/summertskattegrunnlag/nav/2021/$fnr")
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
            accessToken = AccessToken("mockJWT"),
            fnr = fnr,
        ) shouldBe SkatteoppslagFeil.FantIkkePerson.left()
    }

    @Test
    fun `hvis skattegrunnlag ikke eksisterer for fnr og gitt år så mapper vi til tilsvarende skatteoppslagsfeil`() {
        wireMockServer.stubFor(
            WireMock.get("/api/formueinntekt/summertskattegrunnlag/nav/2021/$fnr")
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
            accessToken = AccessToken("mockJWT"),
            fnr = fnr,
        ) shouldBe SkatteoppslagFeil.FantIkkeSkattegrunnlagForGittÅr.left()
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
            WireMock.get("/api/formueinntekt/summertskattegrunnlag/nav/2021/$fnr")
                .willReturn(
                    WireMock.ok(feilFormatPåBody)
                        .withHeader("Content-Type", "application/json"),
                ),
        )

        val response = client.hentSamletSkattegrunnlag(
            accessToken = AccessToken("mockJWT"),
            fnr = fnr,
        )

        response.shouldBeInstanceOf<Either.Left<SkatteoppslagFeil.DeserializeringFeil>>()
    }

    @Test
    fun `feil i data-mappingen håndteres`() {
        val feilformatertFnr = "123 x 567 y"
        wireMockServer.stubFor(
            WireMock.get("/api/formueinntekt/summertskattegrunnlag/nav/2021/$fnr")
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
            accessToken = AccessToken("mockJWT"),
            fnr = fnr,
        ) shouldBe SkatteoppslagFeil.MappingFeil.left()
    }

    @Test
    fun `success response gir mapped data`() {
        wireMockServer.stubFor(
            WireMock.get("/api/formueinntekt/summertskattegrunnlag/nav/2021/$fnr")
                .willReturn(
                    WireMock.ok(
                        """
                        {
                          "personidentifikator": "21839199217",
                          "inntektsaar": "2021",
                          "skjermet": false,
                          "grunnlag": [
                            {
                              "tekniskNavn": "samletLoennsinntektMedTrygdeavgiftspliktOgMedTrekkplikt",
                              "beloep": 762732,
                              "kategori": [
                                "inntekt"
                              ]
                            },
                            {
                              "tekniskNavn": "minstefradragIInntekt",
                              "beloep": 106750,
                              "kategori": [
                                "inntektsfradrag"
                              ]
                            }
                          ],
                          "skatteoppgjoersdato": "2022-02-10"
                        }
                        """.trimIndent(),
                    )
                        .withHeader("Content-Type", "application/json"),
                ),
        )

        client.hentSamletSkattegrunnlag(
            accessToken = AccessToken("mockJWT"),
            fnr = fnr,
        ) shouldBe Skattegrunnlag(
            fnr = fnr,
            inntektsår = 2021,
            grunnlag = listOf(
                Skattegrunnlag.Grunnlag(
                    navn = "samletLoennsinntektMedTrygdeavgiftspliktOgMedTrekkplikt",
                    beløp = 762732,
                    kategori = listOf(Skattegrunnlag.Kategori.INNTEKT),
                ),
                Skattegrunnlag.Grunnlag(
                    navn = "minstefradragIInntekt",
                    beløp = 106750,
                    kategori = listOf(Skattegrunnlag.Kategori.INNTEKTSFRADRAG),
                ),
            ),
            skatteoppgjoersdato = LocalDate.of(2022, 2, 10),
            hentetDato = fixedTidspunkt,
        ).right()
    }
}
