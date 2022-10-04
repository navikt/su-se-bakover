package no.nav.su.se.bakover.client.person

import arrow.core.left
import arrow.core.right
import com.github.tomakehurst.wiremock.client.WireMock
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.client.WiremockBase
import no.nav.su.se.bakover.client.WiremockBase.Companion.wireMockServer
import no.nav.su.se.bakover.client.azure.AzureAd
import no.nav.su.se.bakover.client.stubs.sts.TokenOppslagStub
import no.nav.su.se.bakover.common.AktørId
import no.nav.su.se.bakover.common.ApplicationConfig
import no.nav.su.se.bakover.common.Fnr
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.februar
import no.nav.su.se.bakover.common.token.JwtToken
import no.nav.su.se.bakover.domain.person.KunneIkkeHentePerson
import no.nav.su.se.bakover.domain.person.SivilstandTyper
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
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
            wiremockBuilderSystembruker("Bearer ${tokenOppslag.token().value}")
                .willReturn(WireMock.ok(errorResponseJson)),
        )

        val client = PdlClient(
            PdlClientConfig(
                vars = ApplicationConfig.ClientsConfig.PdlConfig(wireMockServer.baseUrl(), "clientId"),
                tokenOppslag = tokenOppslag,
                azureAd = mock(),
            ),
        )
        client.aktørId(Fnr("12345678912"), JwtToken.BrukerToken("ignored because of mock")) shouldBe KunneIkkeHentePerson.Ukjent.left()
    }

    @Test
    fun `hent aktørid ukjent feil`() {
        wireMockServer.stubFor(
            wiremockBuilderSystembruker("Bearer ${tokenOppslag.token().value}")
                .willReturn(WireMock.serverError()),
        )

        val client = PdlClient(
            PdlClientConfig(
                vars = ApplicationConfig.ClientsConfig.PdlConfig(wireMockServer.baseUrl(), "clientId"),
                tokenOppslag = tokenOppslag,
                azureAd = mock(),
            ),
        )
        client.aktørId(Fnr("12345678912"), JwtToken.BrukerToken("ignored because of mock")) shouldBe KunneIkkeHentePerson.Ukjent.left()
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
        val azureAdMock = mock<AzureAd> {
            on { onBehalfOfToken(any(), any()) } doReturn "etOnBehalfOfToken"
        }

        wireMockServer.stubFor(
            wiremockBuilderOnBehalfOf("Bearer etOnBehalfOfToken")
                .willReturn(WireMock.ok(suksessResponseJson)),
        )

        val client = PdlClient(
            PdlClientConfig(
                vars = ApplicationConfig.ClientsConfig.PdlConfig(wireMockServer.baseUrl(), "clientId"),
                tokenOppslag = tokenOppslag,
                azureAd = azureAdMock,
            ),
        )
        client.aktørId(Fnr("12345678912"), JwtToken.BrukerToken("ignored because of mock")) shouldBe AktørId("2751637578706").right()
    }

    @Test
    fun `hent aktørid OK med kun on behalf of token`() {
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
        val azureAdMock = mock<AzureAd> {
            on { onBehalfOfToken(any(), any()) } doReturn "etOnBehalfOfToken"
        }

        wireMockServer.stubFor(
            wiremockBuilderOnBehalfOf("Bearer etOnBehalfOfToken")
                .willReturn(WireMock.ok(suksessResponseJson)),
        )

        val client = PdlClient(
            PdlClientConfig(
                vars = ApplicationConfig.ClientsConfig.PdlConfig(wireMockServer.baseUrl(), "clientId"),
                tokenOppslag = tokenOppslag,
                azureAd = azureAdMock,
            ),
        )
        client.aktørId(Fnr("12345678912"), JwtToken.BrukerToken("ignored because of mock")) shouldBe AktørId("2751637578706").right()
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
        val azureAdMock = mock<AzureAd> {
            on { onBehalfOfToken(any(), any()) } doReturn "etOnBehalfOfToken"
        }

        wireMockServer.stubFor(
            wiremockBuilderOnBehalfOf("Bearer etOnBehalfOfToken")
                .willReturn(WireMock.ok(errorResponseJson)),
        )

        val client = PdlClient(
            PdlClientConfig(
                vars = ApplicationConfig.ClientsConfig.PdlConfig(wireMockServer.baseUrl(), "clientId"),
                tokenOppslag = tokenOppslag,
                azureAd = azureAdMock,
            ),
        )
        client.person(Fnr("12345678912"), JwtToken.BrukerToken("ignored because of mock")) shouldBe KunneIkkeHentePerson.FantIkkePerson.left()
    }

    @Test
    fun `hent person ukjent feil`() {
        wireMockServer.stubFor(
            wiremockBuilderSystembruker("Bearer ${tokenOppslag.token().value}")
                .willReturn(WireMock.serverError()),
        )

        val client = PdlClient(
            PdlClientConfig(
                vars = ApplicationConfig.ClientsConfig.PdlConfig(wireMockServer.baseUrl(), "clientId"),
                tokenOppslag = tokenOppslag,
                azureAd = mock(),
            ),
        )
        client.person(Fnr("12345678912"), JwtToken.BrukerToken("ignored because of mock")) shouldBe KunneIkkeHentePerson.Ukjent.left()
    }

    @Test
    fun `hent person OK og fjerner duplikate adresser`() {
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
                  "sivilstand": [
                      {
                      "type": "GIFT",
                      "relatertVedSivilstand": "12345678901"
                      }
                  ],
                  "kjoenn": [
                    {
                      "kjoenn": "MANN"
                    }
                  ],
                  "foedsel": [],
                  "adressebeskyttelse": [],
                  "vergemaalEllerFremtidsfullmakt": [],
                  "fullmakt": [],
                  "doedsfall": [
                    {
                      "doedsdato": "2021-12-21"
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
        val azureAdMock = mock<AzureAd> {
            on { onBehalfOfToken(any(), any()) } doReturn "etOnBehalfOfToken"
        }

        wireMockServer.stubFor(
            wiremockBuilderOnBehalfOf("Bearer etOnBehalfOfToken")
                .willReturn(WireMock.ok(suksessResponseJson)),
        )

        val client = PdlClient(
            PdlClientConfig(
                vars = ApplicationConfig.ClientsConfig.PdlConfig(wireMockServer.baseUrl(), "clientId"),
                tokenOppslag = tokenOppslag,
                azureAd = azureAdMock,
            ),
        )
        client.person(Fnr("07028820547"), JwtToken.BrukerToken("ignored because of mock")) shouldBe PdlData(
            ident = PdlData.Ident(Fnr("07028820547"), AktørId("2751637578706")),
            navn = PdlData.Navn(
                fornavn = "NYDELIG",
                mellomnavn = null,
                etternavn = "KRONJUVEL",
            ),
            telefonnummer = null,
            kjønn = "MANN",
            fødselsdato = null,
            adresse = listOf(
                PdlData.Adresse(
                    adresselinje = "SANDTAKVEIEN 42",
                    postnummer = "9190",
                    bruksenhet = null,
                    kommunenummer = "5427",
                    adressetype = "Bostedsadresse",
                    adresseformat = "Vegadresse",
                ),
            ),
            statsborgerskap = "SYR",
            adressebeskyttelse = null,
            vergemålEllerFremtidsfullmakt = false,
            fullmakt = false,
            sivilstand = SivilstandResponse(
                type = SivilstandTyper.GIFT,
                relatertVedSivilstand = "12345678901",
            ),
            dødsdato = 21.desember(2021),
        ).right()
    }

    @Test
    fun `hent person OK og viser alle ulike adresser, the sequel`() {
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
                      "vegadresse": null,
                      "postadresseIFrittFormat": {
                        "adresselinje1": "HER ER POSTLINJE 1",
                        "adresselinje2": "OG POSTLINJE 2",
                        "adresselinje3": null,
                        "postnummer": "9190"
                      },
                      "postboksadresse": null,
                      "utenlandskAdresse": null,
                      "utenlandskAdresseIFrittFormat": null
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
                    },
                      "matrikkeladresse": null,
                      "utenlandskAdresse": null
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
                  "sivilstand": [
                      {
                      "type": "GIFT",
                      "relatertVedSivilstand": "12345678901"
                      }
                  ],
                  "kjoenn": [
                    {
                      "kjoenn": "MANN"
                    }
                  ],
                  "foedsel": [],
                  "adressebeskyttelse": [],
                  "vergemaalEllerFremtidsfullmakt": [],
                  "fullmakt": [],
                  "doedsfall": [
                    {
                      "doedsdato": "2021-12-21"
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
        val azureAdMock = mock<AzureAd> {
            on { onBehalfOfToken(any(), any()) } doReturn "etOnBehalfOfToken"
        }

        wireMockServer.stubFor(
            wiremockBuilderOnBehalfOf("Bearer etOnBehalfOfToken")
                .willReturn(WireMock.ok(suksessResponseJson)),
        )

        val client = PdlClient(
            PdlClientConfig(
                vars = ApplicationConfig.ClientsConfig.PdlConfig(wireMockServer.baseUrl(), "clientId"),
                tokenOppslag = tokenOppslag,
                azureAd = azureAdMock,
            ),
        )
        client.person(Fnr("07028820547"), JwtToken.BrukerToken("ignored because of mock")) shouldBe PdlData(
            ident = PdlData.Ident(Fnr("07028820547"), AktørId("2751637578706")),
            navn = PdlData.Navn(
                fornavn = "NYDELIG",
                mellomnavn = null,
                etternavn = "KRONJUVEL",
            ),
            telefonnummer = null,
            kjønn = "MANN",
            fødselsdato = null,
            adresse = listOf(
                PdlData.Adresse(
                    adresselinje = "SANDTAKVEIEN 42",
                    postnummer = "9190",
                    bruksenhet = null,
                    kommunenummer = "5427",
                    adressetype = "Bostedsadresse",
                    adresseformat = "Vegadresse",
                ),
                PdlData.Adresse(
                    adresselinje = "HER ER POSTLINJE 1, OG POSTLINJE 2",
                    postnummer = "9190",
                    bruksenhet = null,
                    kommunenummer = null,
                    adressetype = "Kontaktadresse",
                    adresseformat = "PostadresseIFrittFormat",
                ),
            ),
            statsborgerskap = "SYR",
            adressebeskyttelse = null,
            vergemålEllerFremtidsfullmakt = false,
            fullmakt = false,
            sivilstand = SivilstandResponse(
                type = SivilstandTyper.GIFT,
                relatertVedSivilstand = "12345678901",
            ),
            dødsdato = 21.desember(2021),
        ).right()
    }

    @Test
    fun `hent person OK og viser alle ulike adresser`() {
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
                      "vegadresse": null,
                      "postadresseIFrittFormat": {
                        "adresselinje1": "HER ER POSTLINJE 1",
                        "adresselinje2": "OG POSTLINJE 2",
                        "adresselinje3": null,
                        "postnummer": "9190"
                      },
                      "postboksadresse": null,
                      "utenlandskAdresse": null,
                      "utenlandskAdresseIFrittFormat": null
                    }
                  ],
                  "oppholdsadresse": [
                    {
                      "vegadresse": null,
                      "matrikkeladresse": {
                        "matrikkelId": 5,
                        "bruksenhetsnummer": "H0606",
                        "tilleggsnavn": "Storgården",
                        "postnummer": "9190",
                        "kommunenummer": "5427"
                      },
                      "utenlandskAdresse": null
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
                  "sivilstand": [
                      {
                      "type": "GIFT",
                      "relatertVedSivilstand": "12345678901"
                      }
                  ],
                  "kjoenn": [
                    {
                      "kjoenn": "MANN"
                    }
                  ],
                  "foedsel": [],
                  "adressebeskyttelse": [],
                  "vergemaalEllerFremtidsfullmakt": [],
                  "fullmakt": [],
                  "doedsfall": []
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
        val azureAdMock = mock<AzureAd> {
            on { onBehalfOfToken(any(), any()) } doReturn "etOnBehalfOfToken"
        }

        wireMockServer.stubFor(
            wiremockBuilderOnBehalfOf("Bearer etOnBehalfOfToken")
                .willReturn(WireMock.ok(suksessResponseJson)),
        )

        val client = PdlClient(
            PdlClientConfig(
                vars = ApplicationConfig.ClientsConfig.PdlConfig(wireMockServer.baseUrl(), "clientId"),
                tokenOppslag = tokenOppslag,
                azureAd = azureAdMock,
            ),
        )
        client.person(Fnr("07028820547"), JwtToken.BrukerToken("ignored because of mock")) shouldBe PdlData(
            ident = PdlData.Ident(Fnr("07028820547"), AktørId("2751637578706")),
            navn = PdlData.Navn(
                fornavn = "NYDELIG",
                mellomnavn = null,
                etternavn = "KRONJUVEL",
            ),
            telefonnummer = null,
            kjønn = "MANN",
            fødselsdato = null,
            adresse = listOf(
                PdlData.Adresse(
                    adresselinje = "SANDTAKVEIEN 42",
                    postnummer = "9190",
                    bruksenhet = null,
                    kommunenummer = "5427",
                    adressetype = "Bostedsadresse",
                    adresseformat = "Vegadresse",
                ),
                PdlData.Adresse(
                    adresselinje = "Storgården",
                    postnummer = "9190",
                    bruksenhet = "H0606",
                    kommunenummer = "5427",
                    adressetype = "Oppholdsadresse",
                    adresseformat = "Matrikkeladresse",
                ),
                PdlData.Adresse(
                    adresselinje = "HER ER POSTLINJE 1, OG POSTLINJE 2",
                    postnummer = "9190",
                    bruksenhet = null,
                    kommunenummer = null,
                    adressetype = "Kontaktadresse",
                    adresseformat = "PostadresseIFrittFormat",
                ),
            ),
            statsborgerskap = "SYR",
            adressebeskyttelse = null,
            vergemålEllerFremtidsfullmakt = false,
            fullmakt = false,
            sivilstand = SivilstandResponse(
                type = SivilstandTyper.GIFT,
                relatertVedSivilstand = "12345678901",
            ),
            dødsdato = null,
        ).right()
    }

    @Test
    fun `hent person OK, men med tomme verdier`() {
        //language=JSON
        val suksessResponseJson =
            """
            {
              "data": {
                "hentPerson": {
                  "navn": [{
                "fornavn": "NYDELIG",
                "mellomnavn": null,
                "etternavn": "KRONJUVEL",
                "metadata": {
                  "master": "Freg"
                 }
                }],
                  "telefonnummer": [],
                  "bostedsadresse": [],
                  "kontaktadresse": [],
                  "oppholdsadresse": [],
                  "statsborgerskap": [],
                  "kjoenn": [],
                  "foedsel": [],
                  "adressebeskyttelse": [],
                  "vergemaalEllerFremtidsfullmakt": [],
                  "fullmakt": [],
                  "sivilstand": [],
                  "doedsfall": []
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
            wiremockBuilderSystembruker("Bearer ${tokenOppslag.token().value}")
                .willReturn(WireMock.ok(suksessResponseJson)),
        )

        val client = PdlClient(
            PdlClientConfig(
                vars = ApplicationConfig.ClientsConfig.PdlConfig(wireMockServer.baseUrl(), "clientId"),
                tokenOppslag = tokenOppslag,
                azureAd = mock(),
            ),
        )
        client.personForSystembruker(Fnr("07028820547")) shouldBe PdlData(
            ident = PdlData.Ident(Fnr("07028820547"), AktørId("2751637578706")),
            navn = PdlData.Navn(
                fornavn = "NYDELIG",
                mellomnavn = null,
                etternavn = "KRONJUVEL",
            ),
            telefonnummer = null,
            kjønn = null,
            fødselsdato = null,
            adresse = emptyList(),
            statsborgerskap = null,
            adressebeskyttelse = null,
            vergemålEllerFremtidsfullmakt = false,
            fullmakt = false,
            sivilstand = null,
            dødsdato = null,
        ).right()
    }

    @Test
    fun `hent person OK med on behalf of token`() {
        //language=JSON
        val suksessResponseJson =
            """
            {
              "data": {
                "hentPerson": {
                  "navn": [{
                "fornavn": "NYDELIG",
                "mellomnavn": null,
                "etternavn": "KRONJUVEL",
                "metadata": {
                  "master": "Freg"
                 }
                }],
                  "telefonnummer": [],
                  "bostedsadresse": [],
                  "kontaktadresse": [],
                  "oppholdsadresse": [],
                  "statsborgerskap": [],
                  "kjoenn": [],
                  "foedsel": [],
                  "adressebeskyttelse": [],
                  "vergemaalEllerFremtidsfullmakt": [],
                  "fullmakt": [],
                  "sivilstand": [],
                  "doedsfall": []
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

        val azureAdMock = mock<AzureAd> {
            on { onBehalfOfToken(any(), any()) } doReturn "etOnBehalfOfToken"
        }

        wireMockServer.stubFor(
            wiremockBuilderOnBehalfOf("Bearer etOnBehalfOfToken")
                .willReturn(WireMock.ok(suksessResponseJson)),
        )

        val client = PdlClient(
            PdlClientConfig(
                vars = ApplicationConfig.ClientsConfig.PdlConfig(wireMockServer.baseUrl(), "clientId"),
                tokenOppslag = tokenOppslag,
                azureAd = azureAdMock,
            ),
        )
        client.person(Fnr("07028820547"), JwtToken.BrukerToken("ignored because of mock")) shouldBe PdlData(
            ident = PdlData.Ident(Fnr("07028820547"), AktørId("2751637578706")),
            navn = PdlData.Navn(
                fornavn = "NYDELIG",
                mellomnavn = null,
                etternavn = "KRONJUVEL",
            ),
            telefonnummer = null,
            kjønn = null,
            fødselsdato = null,
            adresse = emptyList(),
            statsborgerskap = null,
            adressebeskyttelse = null,
            vergemålEllerFremtidsfullmakt = false,
            fullmakt = false,
            sivilstand = null,
            dødsdato = null,
        ).right()
    }

    @Test
    fun `hent person OK for systembruker`() {
        //language=JSON
        val suksessResponseJson =
            """
            {
              "data": {
                "hentPerson": {
                  "navn": [],
                  "telefonnummer": [],
                  "bostedsadresse": [],
                  "kontaktadresse": [],
                  "oppholdsadresse": [],
                  "statsborgerskap": [],
                  "kjoenn": [],
                  "foedsel": [],
                  "adressebeskyttelse": [],
                  "vergemaalEllerFremtidsfullmakt": [],
                  "fullmakt": [],
                  "sivilstand": [],
                  "doedsfall": []
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
            wiremockBuilderSystembruker("Bearer ${tokenOppslag.token().value}")
                .willReturn(WireMock.ok(suksessResponseJson)),
        )

        val client = PdlClient(
            PdlClientConfig(
                vars = ApplicationConfig.ClientsConfig.PdlConfig(wireMockServer.baseUrl(), "clientId"),
                tokenOppslag = tokenOppslag,
                azureAd = mock(),
            ),
        )
        client.personForSystembruker(Fnr("07028820547")) shouldBe KunneIkkeHentePerson.FantIkkePerson.left()
    }

    @Test
    fun `henter første dødsdato som ikke er null`() {
        //language=JSON
        val suksessResponseJson =
            """
            {
              "data": {
                "hentPerson": {
                  "navn": [{
                "fornavn": "NYDELIG",
                "mellomnavn": null,
                "etternavn": "KRONJUVEL",
                "metadata": {
                  "master": "Freg"
                 }
                }],
                  "telefonnummer": [],
                  "bostedsadresse": [],
                  "kontaktadresse": [],
                  "oppholdsadresse": [],
                  "statsborgerskap": [],
                  "kjoenn": [],
                  "foedsel": [],
                  "adressebeskyttelse": [],
                  "vergemaalEllerFremtidsfullmakt": [],
                  "fullmakt": [],
                  "sivilstand": [],
                  "doedsfall": [
                    {
                      "doedsdato": null
                    },
                     {
                      "doedsdato": "2022-02-22"
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
            wiremockBuilderSystembruker("Bearer ${tokenOppslag.token().value}")
                .willReturn(WireMock.ok(suksessResponseJson)),
        )

        val client = PdlClient(
            PdlClientConfig(
                vars = ApplicationConfig.ClientsConfig.PdlConfig(wireMockServer.baseUrl(), "clientId"),
                tokenOppslag = tokenOppslag,
                azureAd = mock(),
            ),
        )
        client.personForSystembruker(Fnr("07028820547")) shouldBe PdlData(
            ident = PdlData.Ident(Fnr("07028820547"), AktørId("2751637578706")),
            navn = PdlData.Navn(
                fornavn = "NYDELIG",
                mellomnavn = null,
                etternavn = "KRONJUVEL",
            ),
            telefonnummer = null,
            kjønn = null,
            fødselsdato = null,
            adresse = emptyList(),
            statsborgerskap = null,
            adressebeskyttelse = null,
            vergemålEllerFremtidsfullmakt = false,
            fullmakt = false,
            sivilstand = null,
            dødsdato = 22.februar(2022),
        ).right()
    }

    private fun wiremockBuilderSystembruker(authorization: String) = WireMock.post(WireMock.urlPathEqualTo("/graphql"))
        .withHeader("Authorization", WireMock.equalTo(authorization))
        .withHeader("Content-Type", WireMock.equalTo("application/json"))
        .withHeader("Accept", WireMock.equalTo("application/json"))
        .withHeader("Nav-Consumer-Token", WireMock.equalTo("Bearer ${tokenOppslag.token().value}"))
        .withHeader("Tema", WireMock.equalTo("SUP"))

    private fun wiremockBuilderOnBehalfOf(authorization: String) = WireMock.post(WireMock.urlPathEqualTo("/graphql"))
        .withHeader("Authorization", WireMock.equalTo(authorization))
        .withHeader("Content-Type", WireMock.equalTo("application/json"))
        .withHeader("Accept", WireMock.equalTo("application/json"))
        .withHeader("Tema", WireMock.equalTo("SUP"))

    @BeforeEach
    fun beforeEach() {
        MDC.put("Authorization", "Bearer abc")
    }
}
