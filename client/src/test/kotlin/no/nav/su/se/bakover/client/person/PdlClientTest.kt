package no.nav.su.se.bakover.client.person

import arrow.core.left
import arrow.core.right
import com.github.tomakehurst.wiremock.client.WireMock
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.client.stubs.azure.AzureClientStub
import no.nav.su.se.bakover.common.auth.AzureAd
import no.nav.su.se.bakover.common.domain.tid.desember
import no.nav.su.se.bakover.common.infrastructure.config.ApplicationConfig
import no.nav.su.se.bakover.common.infrastructure.token.JwtToken
import no.nav.su.se.bakover.common.person.AktørId
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.test.wiremock.startedWireMockServerWithCorrelationId
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import person.domain.KunneIkkeHentePerson
import person.domain.SivilstandTyper
import java.time.LocalDate

internal class PdlClientTest {

    private val tokenOppslag = AzureClientStub

    private val expectedPdlDataTemplate = PdlData(
        ident = PdlData.Ident(Fnr("07028820547"), AktørId("2751637578706")),
        navn = PdlData.Navn(
            fornavn = "NYDELIG",
            mellomnavn = null,
            etternavn = "KRONJUVEL",
        ),
        telefonnummer = null,
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
        sivilstand = SivilstandResponse(
            type = SivilstandTyper.GIFT,
            relatertVedSivilstand = "12345678901",
        ),
        fødsel = null,
        adressebeskyttelse = null,
        vergemålEllerFremtidsfullmakt = false,
        dødsdato = 21.desember(2021),
    )

    @Test
    fun `hent aktørid inneholder errors`() {
        startedWireMockServerWithCorrelationId {
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
              },
              "extensions": {"etAllerAnnetMap":  "her får vi noe warnings i et eller annent format som vi logger"}
            }
                """.trimIndent()
            stubFor(
                wiremockBuilderSystembruker("Bearer ${tokenOppslag.getSystemToken("pdlClientId")}")
                    .willReturn(WireMock.ok(errorResponseJson)),
            )

            val client = PdlClient(
                PdlClientConfig(
                    vars = ApplicationConfig.ClientsConfig.PdlConfig(baseUrl(), "clientId"),
                    azureAd = mock<AzureAd> { on { this.getSystemToken(any()) } doReturn "token" },
                ),
            )
            client.aktørIdMedSystembruker(
                Fnr("12345678912"),
            ) shouldBe KunneIkkeHentePerson.Ukjent.left()
        }
    }

    @Test
    fun `hent aktørid ukjent feil`() {
        startedWireMockServerWithCorrelationId {
            stubFor(
                wiremockBuilderSystembruker("Bearer ${tokenOppslag.getSystemToken("pdlClientId")}")
                    .willReturn(WireMock.serverError()),
            )

            val client = PdlClient(
                PdlClientConfig(
                    vars = ApplicationConfig.ClientsConfig.PdlConfig(baseUrl(), "clientId"),
                    azureAd = mock<AzureAd> { on { this.getSystemToken(any()) } doReturn "token" },
                ),
            )
            client.aktørIdMedSystembruker(
                Fnr("12345678912"),
            ) shouldBe KunneIkkeHentePerson.Ukjent.left()
        }
    }

    @Test
    fun `hent aktørid OK`() {
        startedWireMockServerWithCorrelationId {
            //language=JSON
            val suksessResponseJson =
                """
            {
              "data": {
                "hentIdenter": {
                  "identer": [
                    {
                      "ident": "07028820547",
                      "gruppe": "FOLKEREGISTERIDENT",
                      "historisk": false
                    },
                    {
                      "ident": "2751637578706",
                      "gruppe": "AKTORID",
                      "historisk": false
                    }
                  ]
                }
              },
              "extensions": null
            }
                """.trimIndent()
            val azureAdMock = mock<AzureAd> {
                on { getSystemToken(any()) } doReturn "etOnBehalfOfToken"
            }

            stubFor(
                wiremockBuilderOnBehalfOf("Bearer etOnBehalfOfToken")
                    .willReturn(WireMock.ok(suksessResponseJson)),
            )

            val client = PdlClient(
                PdlClientConfig(
                    vars = ApplicationConfig.ClientsConfig.PdlConfig(baseUrl(), "clientId"),
                    azureAd = azureAdMock,
                ),
            )
            client.aktørIdMedSystembruker(
                Fnr("12345678912"),
            ) shouldBe AktørId("2751637578706").right()
        }
    }

    @Test
    fun `hent aktørid OK med kun on behalf of token`() {
        startedWireMockServerWithCorrelationId {
            //language=JSON
            val suksessResponseJson =
                """
            {
              "data": {
                "hentIdenter": {
                  "identer": [
                    {
                      "ident": "07028820547",
                      "gruppe": "FOLKEREGISTERIDENT",
                      "historisk": false
                    },
                    {
                      "ident": "2751637578706",
                      "gruppe": "AKTORID",
                      "historisk": false
                    }
                  ]
                }
              }
            }
                """.trimIndent()
            val azureAdMock = mock<AzureAd> {
                on { getSystemToken(any()) } doReturn "etOnBehalfOfToken"
            }

            stubFor(
                wiremockBuilderOnBehalfOf("Bearer etOnBehalfOfToken")
                    .willReturn(WireMock.ok(suksessResponseJson)),
            )

            val client = PdlClient(
                PdlClientConfig(
                    vars = ApplicationConfig.ClientsConfig.PdlConfig(baseUrl(), "clientId"),
                    azureAd = azureAdMock,
                ),
            )
            client.aktørIdMedSystembruker(
                Fnr("12345678912"),
            ) shouldBe AktørId("2751637578706").right()
        }
    }

    @Test
    fun `hent person inneholder kjent feil`() {
        startedWireMockServerWithCorrelationId {
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

            stubFor(
                wiremockBuilderOnBehalfOf("Bearer etOnBehalfOfToken")
                    .willReturn(WireMock.ok(errorResponseJson)),
            )

            val client = PdlClient(
                PdlClientConfig(
                    vars = ApplicationConfig.ClientsConfig.PdlConfig(baseUrl(), "clientId"),
                    azureAd = azureAdMock,
                ),
            )
            client.person(
                Fnr("12345678912"),
                JwtToken.BrukerToken("ignored because of mock"),
            ) shouldBe KunneIkkeHentePerson.FantIkkePerson.left()
        }
    }

    @Test
    fun `hent person ukjent feil`() {
        startedWireMockServerWithCorrelationId {
            stubFor(
                wiremockBuilderSystembruker("Bearer ${tokenOppslag.getSystemToken("pdlClientId")}")
                    .willReturn(WireMock.serverError()),
            )

            val client = PdlClient(
                PdlClientConfig(
                    vars = ApplicationConfig.ClientsConfig.PdlConfig(baseUrl(), "clientId"),
                    azureAd = mock<AzureAd> { on { this.getSystemToken(any()) } doReturn "token" },
                ),
            )
            client.person(
                Fnr("12345678912"),
                JwtToken.BrukerToken("ignored because of mock"),
            ) shouldBe KunneIkkeHentePerson.Ukjent.left()
        }
    }

    @Test
    fun `hent person OK og fjerner duplikate adresser`() {
        startedWireMockServerWithCorrelationId {
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
                        "master": "Freg",
                        "historisk": false
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
                  "foedselsdato": [
                  {
                    "foedselsdato": "2021-12-21",
                    "foedselsaar": 2021
                  }
      ],
                  "adressebeskyttelse": [],
                  "vergemaalEllerFremtidsfullmakt": [],
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
                      "gruppe": "FOLKEREGISTERIDENT",
                      "historisk": false
                    },
                    {
                      "ident": "2751637578706",
                      "gruppe": "AKTORID",
                      "historisk": false
                    }
                  ]
                }
              }
            }
                """.trimIndent()
            val azureAdMock = mock<AzureAd> {
                on { onBehalfOfToken(any(), any()) } doReturn "etOnBehalfOfToken"
            }

            stubFor(
                wiremockBuilderOnBehalfOf("Bearer etOnBehalfOfToken")
                    .willReturn(WireMock.ok(suksessResponseJson)),
            )

            val client = PdlClient(
                PdlClientConfig(
                    vars = ApplicationConfig.ClientsConfig.PdlConfig(baseUrl(), "clientId"),
                    azureAd = azureAdMock,
                ),
            )
            client.person(
                Fnr("07028820547"),
                JwtToken.BrukerToken("ignored because of mock"),
            ) shouldBe expectedPdlDataTemplate.copy(
                fødsel = PdlData.Fødsel(
                    foedselsdato = LocalDate.of(2021, 12, 21),
                    foedselsaar = 2021,
                ),
            ).right()
        }
    }

    @Test
    fun `hent person OK og viser alle ulike adresser, the sequel`() {
        startedWireMockServerWithCorrelationId {
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
                        "master": "Freg",
                        "historisk": false
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
                  "foedselsdato": [],
                  "adressebeskyttelse": [],
                  "vergemaalEllerFremtidsfullmakt": [],
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
                      "gruppe": "FOLKEREGISTERIDENT",
                      "historisk": false
                    },
                    {
                      "ident": "2751637578706",
                      "gruppe": "AKTORID",
                      "historisk": false
                    }
                  ]
                }
              }
            }
                """.trimIndent()
            val azureAdMock = mock<AzureAd> {
                on { onBehalfOfToken(any(), any()) } doReturn "etOnBehalfOfToken"
            }

            stubFor(
                wiremockBuilderOnBehalfOf("Bearer etOnBehalfOfToken")
                    .willReturn(WireMock.ok(suksessResponseJson)),
            )

            val client = PdlClient(
                PdlClientConfig(
                    vars = ApplicationConfig.ClientsConfig.PdlConfig(baseUrl(), "clientId"),
                    azureAd = azureAdMock,
                ),
            )
            client.person(
                Fnr("07028820547"),
                JwtToken.BrukerToken("ignored because of mock"),
            ) shouldBe expectedPdlDataTemplate.copy(
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
            ).right()
        }
    }

    @Test
    fun `hent person OK og viser alle ulike adresser`() {
        startedWireMockServerWithCorrelationId {
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
                        "master": "Freg",
                        "historisk": false
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
                  "foedselsdato": [],
                  "adressebeskyttelse": [],
                  "vergemaalEllerFremtidsfullmakt": [],
                  "doedsfall": []
                },
                "hentIdenter": {
                  "identer": [
                    {
                      "ident": "07028820547",
                      "gruppe": "FOLKEREGISTERIDENT",
                      "historisk": false
                    },
                    {
                      "ident": "2751637578706",
                      "gruppe": "AKTORID",
                      "historisk": false
                    }
                  ]
                }
              }
            }
                """.trimIndent()
            val azureAdMock = mock<AzureAd> {
                on { onBehalfOfToken(any(), any()) } doReturn "etOnBehalfOfToken"
            }

            stubFor(
                wiremockBuilderOnBehalfOf("Bearer etOnBehalfOfToken")
                    .willReturn(WireMock.ok(suksessResponseJson)),
            )

            val client = PdlClient(
                PdlClientConfig(
                    vars = ApplicationConfig.ClientsConfig.PdlConfig(baseUrl(), "clientId"),
                    azureAd = azureAdMock,
                ),
            )
            client.person(
                Fnr("07028820547"),
                JwtToken.BrukerToken("ignored because of mock"),
            ) shouldBe expectedPdlDataTemplate.copy(
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
                dødsdato = null,
            ).right()
        }
    }

    @Test
    fun `hent person OK, men med tomme verdier`() {
        startedWireMockServerWithCorrelationId {
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
                  "master": "Freg",
                        "historisk": false
                 }
                }],
                  "telefonnummer": [],
                  "bostedsadresse": [],
                  "kontaktadresse": [],
                  "oppholdsadresse": [],
                  "statsborgerskap": [],
                  "foedselsdato": [],
                  "adressebeskyttelse": [],
                  "vergemaalEllerFremtidsfullmakt": [],
                  "sivilstand": [],
                  "doedsfall": []
                },
                "hentIdenter": {
                  "identer": [
                    {
                      "ident": "07028820547",
                      "gruppe": "FOLKEREGISTERIDENT",
                      "historisk": false
                    },
                    {
                      "ident": "2751637578706",
                      "gruppe": "AKTORID",
                      "historisk": false
                    }
                  ]
                }
              }
            }
                """.trimIndent()
            stubFor(
                wiremockBuilderSystembruker("Bearer ${tokenOppslag.getSystemToken("pdlClientId")}")
                    .willReturn(WireMock.ok(suksessResponseJson)),
            )

            val client = PdlClient(
                PdlClientConfig(
                    vars = ApplicationConfig.ClientsConfig.PdlConfig(baseUrl(), "clientId"),
                    azureAd = mock<AzureAd> { on { this.getSystemToken(any()) } doReturn "token" },
                ),
            )
            client.personForSystembruker(Fnr("07028820547")) shouldBe expectedPdlDataTemplate.copy(
                adresse = emptyList(),
                sivilstand = null,
                dødsdato = null,
                statsborgerskap = null,
            ).right()
        }
    }

    @Test
    fun `hent person OK med on behalf of token`() {
        startedWireMockServerWithCorrelationId {
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
                  "master": "Freg",
                        "historisk": false
                 }
                }],
                  "telefonnummer": [],
                  "bostedsadresse": [],
                  "kontaktadresse": [],
                  "oppholdsadresse": [],
                  "statsborgerskap": [],
                  "foedselsdato": [],
                  "adressebeskyttelse": [],
                  "vergemaalEllerFremtidsfullmakt": [],
                  "sivilstand": [],
                  "doedsfall": []
                },
                "hentIdenter": {
                  "identer": [
                    {
                      "ident": "07028820547",
                      "gruppe": "FOLKEREGISTERIDENT",
                      "historisk": false
                    },
                    {
                      "ident": "2751637578706",
                      "gruppe": "AKTORID",
                      "historisk": false
                    }
                  ]
                }
              }
            }
                """.trimIndent()

            val azureAdMock = mock<AzureAd> {
                on { onBehalfOfToken(any(), any()) } doReturn "etOnBehalfOfToken"
            }

            stubFor(
                wiremockBuilderOnBehalfOf("Bearer etOnBehalfOfToken")
                    .willReturn(WireMock.ok(suksessResponseJson)),
            )

            val client = PdlClient(
                PdlClientConfig(
                    vars = ApplicationConfig.ClientsConfig.PdlConfig(baseUrl(), "clientId"),
                    azureAd = azureAdMock,
                ),
            )
            client.person(
                Fnr("07028820547"),
                JwtToken.BrukerToken("ignored because of mock"),
            ) shouldBe expectedPdlDataTemplate.copy(
                adresse = emptyList(),
                sivilstand = null,
                dødsdato = null,
                statsborgerskap = null,
            ).right()
        }
    }

    @Test
    fun `hent person OK for systembruker`() {
        startedWireMockServerWithCorrelationId {
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
                  "foedselsdato": [],
                  "adressebeskyttelse": [],
                  "vergemaalEllerFremtidsfullmakt": [],
                  "sivilstand": [],
                  "doedsfall": []
                },
                "hentIdenter": {
                  "identer": [
                    {
                      "ident": "07028820547",
                      "gruppe": "FOLKEREGISTERIDENT",
                      "historisk": false
                    },
                    {
                      "ident": "2751637578706",
                      "gruppe": "AKTORID",
                      "historisk": false
                    }
                  ]
                }
              }
            }
                """.trimIndent()
            stubFor(
                wiremockBuilderSystembruker("Bearer ${tokenOppslag.getSystemToken("pdlClientId")}")
                    .willReturn(WireMock.ok(suksessResponseJson)),
            )

            val client = PdlClient(
                PdlClientConfig(
                    vars = ApplicationConfig.ClientsConfig.PdlConfig(baseUrl(), "clientId"),
                    azureAd = mock<AzureAd> { on { this.getSystemToken(any()) } doReturn "token" },
                ),
            )
            client.personForSystembruker(Fnr("07028820547")) shouldBe KunneIkkeHentePerson.FantIkkePerson.left()
        }
    }

    @Test
    fun `henter første dødsdato som ikke er null`() {
        startedWireMockServerWithCorrelationId {
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
                  "master": "Freg",
                        "historisk": false
                 }
                }],
                  "telefonnummer": [],
                  "bostedsadresse": [],
                  "kontaktadresse": [],
                  "oppholdsadresse": [],
                  "statsborgerskap": [],
                  "foedselsdato": [],
                  "adressebeskyttelse": [],
                  "vergemaalEllerFremtidsfullmakt": [],
                  "sivilstand": [],
                  "doedsfall": [
                    {
                      "doedsdato": null
                    },
                     {
                      "doedsdato": "2021-12-21"
                     }
                  ]
                },
                "hentIdenter": {
                  "identer": [
                    {
                      "ident": "07028820547",
                      "gruppe": "FOLKEREGISTERIDENT",
                      "historisk": false
                    },
                    {
                      "ident": "2751637578706",
                      "gruppe": "AKTORID",
                      "historisk": false
                    }
                  ]
                }
              }
            }
                """.trimIndent()
            stubFor(
                wiremockBuilderSystembruker("Bearer ${tokenOppslag.getSystemToken("pdlClientId")}")
                    .willReturn(WireMock.ok(suksessResponseJson)),
            )

            val client = PdlClient(
                PdlClientConfig(
                    vars = ApplicationConfig.ClientsConfig.PdlConfig(baseUrl(), "clientId"),
                    azureAd = mock<AzureAd> { on { this.getSystemToken(any()) } doReturn "token" },
                ),
            )
            client.personForSystembruker(Fnr("07028820547")) shouldBe expectedPdlDataTemplate.copy(
                adresse = emptyList(),
                sivilstand = null,
                statsborgerskap = null,
            ).right()
        }
    }

    private fun wiremockBuilderSystembruker(authorization: String) = WireMock.post(WireMock.urlPathEqualTo("/graphql"))
        .withHeader("Authorization", WireMock.equalTo(authorization))
        .withHeader("Content-Type", WireMock.equalTo("application/json"))
        .withHeader("Accept", WireMock.equalTo("application/json"))
        .withHeader("Tema", WireMock.equalTo("SUP"))

    private fun wiremockBuilderOnBehalfOf(authorization: String) = WireMock.post(WireMock.urlPathEqualTo("/graphql"))
        .withHeader("Authorization", WireMock.equalTo(authorization))
        .withHeader("Content-Type", WireMock.equalTo("application/json"))
        .withHeader("Accept", WireMock.equalTo("application/json"))
        .withHeader("Tema", WireMock.equalTo("SUP"))
}
