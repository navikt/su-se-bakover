package no.nav.su.se.bakover.client.person

import arrow.core.left
import arrow.core.right
import com.github.tomakehurst.wiremock.client.WireMock
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.client.ClientError
import no.nav.su.se.bakover.client.WiremockBase
import no.nav.su.se.bakover.client.WiremockBase.Companion.wireMockServer
import no.nav.su.se.bakover.client.azure.OAuth
import no.nav.su.se.bakover.client.stubs.sts.TokenOppslagStub
import no.nav.su.se.bakover.domain.AktørId
import no.nav.su.se.bakover.domain.Fnr
import org.json.JSONObject
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.MDC

private const val CLIENT_ID = "clientId"

internal class PdlClientTest : WiremockBase {

    val tokenOppslag = TokenOppslagStub

    @Test
    fun `hent aktørid inneholder errors`() {

        //language=JSON
        val errorResponseJson =
            """
          {
              "errors": [
                {
                  "message": "Ikke autentisert",
                  "locations": [
                    {
                      "line": 2,
                      "column": 3
                    }
                  ],
                  "path": [
                    "hentIdenter"
                  ],
                  "extensions": {
                    "code": "unauthenticated",
                    "classification": "ExecutionAborted"
                  }
                }
              ],
              "data": {
                "hentIdenter": null
              }
            }
            """.trimIndent()
        wireMockServer.stubFor(
            wiremockBuilder
                .willReturn(WireMock.ok(errorResponseJson))
        )

        val client = PdlClient(wireMockServer.baseUrl(), tokenOppslag, CLIENT_ID, tokenExchange)
        client.aktørId(Fnr("12345678912")) shouldBe ClientError(200, "Feil i kallet mot pdl").left()
    }

    @Test
    fun `hent aktørid ukjent feil`() {

        wireMockServer.stubFor(
            wiremockBuilder
                .willReturn(WireMock.serverError())
        )

        val client = PdlClient(wireMockServer.baseUrl(), tokenOppslag, CLIENT_ID, tokenExchange)
        client.aktørId(Fnr("12345678912")) shouldBe ClientError(500, "Feil i kallet mot pdl.").left()
    }

    @Test
    fun `hent aktørid OK`() {

        //language=JSON
        val suksessResponseJson =
            """
            {
              "data": {
                "hentIdenter": {
                  "identer": [
                    {
                      "ident": "07028820547",
                      "gruppe": "FOLKEREGISTERIDENT"
                    },
                    {
                      "ident": "2751637578706",
                      "gruppe": "AKTORID"
                    }
                  ]
                }
              }
            }
            """.trimIndent()
        wireMockServer.stubFor(
            wiremockBuilder
                .willReturn(WireMock.ok(suksessResponseJson))
        )

        val client = PdlClient(wireMockServer.baseUrl(), tokenOppslag, CLIENT_ID, tokenExchange)
        client.aktørId(Fnr("12345678912")) shouldBe AktørId("2751637578706").right()
    }

    @Test
    fun `hent person inneholder errors`() {

        //language=JSON
        val errorResponseJson =
            """
          {
              "errors": [
                {
                  "message": "Ikke autentisert",
                  "locations": [
                    {
                      "line": 2,
                      "column": 3
                    }
                  ],
                  "path": [
                    "hentPerson"
                  ],
                  "extensions": {
                    "code": "unauthenticated",
                    "classification": "ExecutionAborted"
                  }
                }
              ],
              "data": {
                "hentPerson": null
              }
            }
            """.trimIndent()
        wireMockServer.stubFor(
            wiremockBuilder
                .willReturn(WireMock.ok(errorResponseJson))
        )

        val client = PdlClient(wireMockServer.baseUrl(), tokenOppslag, CLIENT_ID, tokenExchange)
        client.person(Fnr("12345678912")) shouldBe ClientError(200, "Feil i kallet mot pdl").left()
    }

    @Test
    fun `hent person ukjent feil`() {

        wireMockServer.stubFor(
            wiremockBuilder
                .willReturn(WireMock.serverError())
        )

        val client = PdlClient(wireMockServer.baseUrl(), tokenOppslag, CLIENT_ID, tokenExchange)
        client.person(Fnr("12345678912")) shouldBe ClientError(500, "Feil i kallet mot pdl.").left()
    }

    @Test
    fun `hent person OK`() {

        //language=JSON
        val suksessResponseJson =
            """
            {
              "data": {
                "hentPerson": {
                  "navn": [
                    {
                      "fornavn": "NYDELIG",
                      "mellomnavn": null,
                      "etternavn": "KRONJUVEL",
                      "metadata": {
                        "master": "Freg"
                      }
                    }
                  ],
                  "telefonnummer": [],
                  "bostedsadresse": [
                    {
                      "vegadresse": {
                        "husnummer": "42",
                        "husbokstav": null,
                        "adressenavn": "SANDTAKVEIEN",
                        "kommunenummer": "5427",
                        "postnummer": "9190",
                        "bruksenhetsnummer": null
                      }
                    }
                  ],
                  "kontaktadresse": [
                    {
                      "vegadresse": {
                        "husnummer": "42",
                        "husbokstav": null,
                        "adressenavn": "SANDTAKVEIEN",
                        "kommunenummer": null,
                        "postnummer": "9190",
                        "bruksenhetsnummer": null
                      }
                    }
                  ],
                  "oppholdsadresse": [
                    {
                      "vegadresse": {
                        "husnummer": "42",
                        "husbokstav": null,
                        "adressenavn": "SANDTAKVEIEN",
                        "kommunenummer": "5427",
                        "postnummer": "9190",
                        "bruksenhetsnummer": null
                      }
                    }
                  ],
                  "statsborgerskap": [
                    {
                      "land": "SYR",
                      "gyldigFraOgMed": null,
                      "gyldigTilOgMed": null
                    },
                    {
                      "land": "SYR",
                      "gyldigFraOgMed": null,
                      "gyldigTilOgMed": null
                    }
                  ],
                  "kjoenn": [
                    {
                      "kjoenn": "MANN"
                    }
                  ]
                },
                "hentIdenter": {
                  "identer": [
                    {
                      "ident": "07028820547",
                      "gruppe": "FOLKEREGISTERIDENT"
                    },
                    {
                      "ident": "2751637578706",
                      "gruppe": "AKTORID"
                    }
                  ]
                }
              }
            }
            """.trimIndent()
        wireMockServer.stubFor(
            wiremockBuilder
                .willReturn(WireMock.ok(suksessResponseJson))
        )

        val client = PdlClient(wireMockServer.baseUrl(), tokenOppslag, CLIENT_ID, tokenExchange)
        client.person(Fnr("07028820547")) shouldBe PdlData(
            ident = PdlData.Ident(Fnr("07028820547"), AktørId("2751637578706")),
            navn = PdlData.Navn(
                fornavn = "NYDELIG",
                mellomnavn = null,
                etternavn = "KRONJUVEL"
            ),
            telefonnummer = null,
            kjønn = "MANN",
            adresse = PdlData.Adresse(
                adressenavn = "SANDTAKVEIEN",
                husnummer = "42",
                husbokstav = null,
                postnummer = "9190",
                bruksenhet = null,
                kommunenummer = "5427"
            ),
            statsborgerskap = "SYR"
        ).right()
    }

    private val tokenExchange = object : OAuth {
        override fun onBehalfOFToken(originalToken: String, otherAppId: String): String = "ON BEHALF OF!"
        override fun refreshTokens(refreshToken: String): JSONObject = JSONObject("""{"access_token":"abc","refresh_token":"cba"}""")
        override fun jwkConfig() = JSONObject()
    }

    private val wiremockBuilder = WireMock.post(WireMock.urlPathEqualTo("/graphql"))
        .withHeader("Authorization", WireMock.equalTo("Bearer ON BEHALF OF!"))
        .withHeader("Content-Type", WireMock.equalTo("application/json"))
        .withHeader("Accept", WireMock.equalTo("application/json"))
        .withHeader("Nav-Consumer-Token", WireMock.equalTo("Bearer ${tokenOppslag.token()}"))
        .withHeader("Tema", WireMock.equalTo("SUP"))

    @BeforeEach
    fun beforeEach() {
        MDC.put("Authorization", "abc")
    }
}
