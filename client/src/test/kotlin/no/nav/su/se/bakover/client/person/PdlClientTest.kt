package no.nav.su.se.bakover.client.person

import arrow.core.left
import arrow.core.right
import com.github.tomakehurst.wiremock.client.WireMock
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.client.WiremockBase
import no.nav.su.se.bakover.client.WiremockBase.Companion.wireMockServer
import no.nav.su.se.bakover.client.stubs.sts.TokenOppslagStub
import no.nav.su.se.bakover.domain.AktørId
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.person.PersonOppslag.KunneIkkeHentePerson
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.MDC

internal class PdlClientTest : WiremockBase {

    private val tokenOppslag = TokenOppslagStub

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

        val client = PdlClient(wireMockServer.baseUrl(), tokenOppslag)
        client.aktørId(Fnr("12345678912")) shouldBe KunneIkkeHentePerson.Ukjent.left()
    }

    @Test
    fun `hent aktørid ukjent feil`() {

        wireMockServer.stubFor(
            wiremockBuilder
                .willReturn(WireMock.serverError())
        )

        val client = PdlClient(wireMockServer.baseUrl(), tokenOppslag)
        client.aktørId(Fnr("12345678912")) shouldBe KunneIkkeHentePerson.Ukjent.left()
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

        val client = PdlClient(wireMockServer.baseUrl(), tokenOppslag)
        client.aktørId(Fnr("12345678912")) shouldBe AktørId("2751637578706").right()
    }

    @Test
    fun `hent person inneholder kjent feil`() {

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
                    "code": "not_found",
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

        val client = PdlClient(wireMockServer.baseUrl(), tokenOppslag)
        client.person(Fnr("12345678912")) shouldBe KunneIkkeHentePerson.FantIkkePerson.left()
    }

    @Test
    fun `hent person ukjent feil`() {

        wireMockServer.stubFor(
            wiremockBuilder
                .willReturn(WireMock.serverError())
        )

        val client = PdlClient(wireMockServer.baseUrl(), tokenOppslag)
        client.person(Fnr("12345678912")) shouldBe KunneIkkeHentePerson.Ukjent.left()
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
                  ],
                  "adressebeskyttelse": [],
                  "vergemaalEllerFremtidsfullmakt": []
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

        val client = PdlClient(wireMockServer.baseUrl(), tokenOppslag)
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
            statsborgerskap = "SYR",
            adressebeskyttelse = null,
            vergemaalEllerFremtidsfullmakt = null
        ).right()
    }

    private val wiremockBuilder = WireMock.post(WireMock.urlPathEqualTo("/graphql"))
        .withHeader("Authorization", WireMock.equalTo("Bearer abc"))
        .withHeader("Content-Type", WireMock.equalTo("application/json"))
        .withHeader("Accept", WireMock.equalTo("application/json"))
        .withHeader("Nav-Consumer-Token", WireMock.equalTo("Bearer ${tokenOppslag.token()}"))
        .withHeader("Tema", WireMock.equalTo("SUP"))

    @BeforeEach
    fun beforeEach() {
        MDC.put("Authorization", "Bearer abc")
    }
}
