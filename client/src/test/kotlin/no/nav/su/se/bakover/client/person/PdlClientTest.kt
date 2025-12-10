package no.nav.su.se.bakover.client.person

import Bostedsadresse
import Kontaktadresse
import Matrikkeladresse
import Oppholdsadresse
import PostadresseIFrittFormat
import Postboksadresse
import UkjentBosted
import UtenlandskAdresse
import UtenlandskAdresseIFrittFormat
import Vegadresse
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
import no.nav.su.se.bakover.common.serialize
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
            val errorResponse = PdlResponse(
                data = IdentResponseData(
                    hentIdenter = null,
                ),
                errors = listOf(
                    PdlError(
                        message = "Ikke autentisert",
                        path = listOf("hentIdenter"),
                        extensions = PdlErrorExtension(
                            code = "unauthenticated",
                        ),
                    ),
                ),
                extensions = mapOf(
                    "etAllerAnnetMap" to "her får vi noe warnings i et eller annent format som vi logger",
                ),
            ).let { serialize(it) }
            stubFor(
                wiremockBuilderSystembruker("Bearer ${tokenOppslag.getSystemToken("pdlClientId")}")
                    .willReturn(WireMock.ok(errorResponse)),
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
            val suksessResponseJson = PdlResponse(
                data = IdentResponseData(
                    hentIdenter = HentIdenter(
                        identer = listOf(
                            Id(
                                ident = "07028820547",
                                gruppe = "FOLKEREGISTERIDENT",
                                historisk = false,
                            ),
                            Id(
                                ident = "2751637578706",
                                gruppe = "AKTORID",
                                historisk = false,
                            ),
                        ),
                    ),
                ),
                errors = null,
                extensions = null,
            ).let {
                serialize(it)
            }
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
            val suksessResponseJson = PdlResponse(
                data = IdentResponseData(
                    hentIdenter = HentIdenter(
                        identer = listOf(
                            Id(
                                ident = "07028820547",
                                gruppe = "FOLKEREGISTERIDENT",
                                historisk = false,
                            ),
                            Id(
                                ident = "2751637578706",
                                gruppe = "AKTORID",
                                historisk = false,
                            ),
                        ),
                    ),
                ),
                errors = null,
                extensions = null,
            ).let { serialize(it) }
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
            val errorResponseJson = PdlResponse(
                data = PersonResponseData(
                    hentPerson = null,
                    hentIdenter = null,
                ),
                errors = listOf(
                    PdlError(
                        message = "Ikke autentisert",
                        path = listOf("hentPerson"),
                        extensions = PdlErrorExtension(
                            code = "not_found",
                        ),
                    ),
                ),
                extensions = null,
            ).let { serialize(it) }

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
        val token = "etOnBehalfOfToken"
        startedWireMockServerWithCorrelationId(token = token) {
            val suksessResponseJson = PdlResponse(
                data = PersonResponseData(
                    HentPerson(
                        navn = listOf(
                            NavnResponse(
                                fornavn = "NYDELIG",
                                mellomnavn = null,
                                etternavn = "KRONJUVEL",
                                metadata = Metadata(
                                    master = "Freg",
                                    historisk = false,
                                ),
                            ),
                        ),
                        telefonnummer = emptyList(),
                        bostedsadresse = listOf(
                            Bostedsadresse(
                                vegadresse = Vegadresse(
                                    husnummer = "42",
                                    husbokstav = null,
                                    adressenavn = "SANDTAKVEIEN",
                                    kommunenummer = "5427",
                                    postnummer = "9190",
                                    bruksenhetsnummer = null,
                                ),
                                ukjentBosted = UkjentBosted(
                                    bostedskommune = "oslo",
                                ),
                                matrikkeladresse = Matrikkeladresse(
                                    matrikkelId = null,
                                    bruksenhetsnummer = "34",
                                    tilleggsnavn = "BLABLA",
                                    postnummer = "9190",
                                    kommunenummer = "5427",
                                ),
                            ),
                        ),
                        kontaktadresse = listOf(
                            Kontaktadresse(
                                vegadresse = Vegadresse(
                                    husnummer = "42",
                                    husbokstav = null,
                                    adressenavn = "SANDTAKVEIEN",
                                    kommunenummer = "5427",
                                    postnummer = "9190",
                                    bruksenhetsnummer = null,
                                ),
                                postadresseIFrittFormat = PostadresseIFrittFormat(
                                    adresselinje1 = "HER ER POSTLINJE 1",
                                    adresselinje2 = "OG POSTLINJE 2",
                                    adresselinje3 = "POSTLINJE 3",
                                    postnummer = "9190",
                                ),
                                postboksadresse = Postboksadresse(
                                    postbokseier = "POSTBOKS EIER",
                                    postboks = "POSTBOKS 123",
                                    postnummer = "9190",
                                ),
                                utenlandskAdresse = UtenlandskAdresse(
                                    adressenavnNummer = "ADDRESS NAME NUMBER",
                                    bygningEtasjeLeilighet = "BUILDING FLOOR APARTMENT",
                                    postboksNummerNavn = "PO BOX NUMBER NAME",
                                    postkode = "CITY OR PLACE NAME",
                                    bySted = "POST CODE",
                                    regionDistriktOmraade = "REGION DISTRICT AREA",
                                    landkode = "LAND CODE",
                                ),
                                utenlandskAdresseIFrittFormat = UtenlandskAdresseIFrittFormat(
                                    adresselinje1 = "FOREIGN ADDRESS LINE 1",
                                    adresselinje2 = "FOREIGN ADDRESS LINE 2",
                                    adresselinje3 = "FOREIGN ADDRESS LINE 3",
                                    postkode = "POST CODE",
                                    byEllerStedsnavn = "CITY OR PLACE NAME",
                                    landkode = "LAND CODE",
                                ),
                            ),
                        ),
                        oppholdsadresse = listOf(
                            Oppholdsadresse(
                                vegadresse = Vegadresse(
                                    husnummer = "42",
                                    husbokstav = null,
                                    adressenavn = "SANDTAKVEIEN",
                                    kommunenummer = "5427",
                                    postnummer = "9190",
                                    bruksenhetsnummer = null,
                                ),
                                matrikkeladresse = Matrikkeladresse(
                                    matrikkelId = null,
                                    bruksenhetsnummer = "34",
                                    tilleggsnavn = "BLABLA",
                                    postnummer = "9190",
                                    kommunenummer = "5427",
                                ),
                                utenlandskAdresse = UtenlandskAdresse(
                                    adressenavnNummer = "ADDRESS NAME NUMBER",
                                    bygningEtasjeLeilighet = "BUILDING FLOOR APARTMENT",
                                    postboksNummerNavn = "PO BOX NUMBER NAME",
                                    postkode = "CITY OR PLACE NAME",
                                    bySted = "POST CODE",
                                    regionDistriktOmraade = "REGION DISTRICT AREA",
                                    landkode = "LAND CODE",
                                ),
                            ),
                        ),
                        statsborgerskap = listOf(
                            Statsborgerskap(
                                land = "SYR",
                                gyldigFraOgMed = null,
                                gyldigTilOgMed = null,
                            ),
                        ),
                        sivilstand = listOf(
                            SivilstandResponse(
                                type = SivilstandTyper.GIFT,
                                relatertVedSivilstand = "12345678901",
                            ),
                        ),
                        foedselsdato = listOf(
                            Fødselsdato(
                                foedselsdato = LocalDate.of(2021, 12, 21),
                                foedselsaar = 2021,
                            ),
                        ),
                        adressebeskyttelse = emptyList(),
                        vergemaalEllerFremtidsfullmakt = emptyList(),
                        doedsfall = listOf(
                            Doedsfall(
                                doedsdato = LocalDate.of(2021, 12, 21),
                            ),
                        ),
                    ),
                    hentIdenter = HentIdenter(
                        identer = listOf(
                            Id(
                                ident = "07028820547",
                                gruppe = "FOLKEREGISTERIDENT",
                                historisk = false,
                            ),
                            Id(
                                ident = "2751637578706",
                                gruppe = "AKTORID",
                                historisk = false,
                            ),
                        ),
                    ),
                ),
                errors = null,
                extensions = null,
            ).let { serialize(it) }
            val azureAdMock = mock<AzureAd> {
                on { onBehalfOfToken(any(), any()) } doReturn token
            }

            stubFor(
                wiremockBuilderOnBehalfOf("Bearer $token")
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
            val suksessResponseJson = PdlResponse(
                data = PersonResponseData(
                    HentPerson(
                        navn = listOf(
                            NavnResponse(
                                fornavn = "NYDELIG",
                                mellomnavn = null,
                                etternavn = "KRONJUVEL",
                                metadata = Metadata(
                                    master = "Freg",
                                    historisk = false,
                                ),
                            ),
                        ),
                        telefonnummer = emptyList(),
                        bostedsadresse = listOf(
                            Bostedsadresse(
                                vegadresse = Vegadresse(
                                    husnummer = "42",
                                    husbokstav = null,
                                    adressenavn = "SANDTAKVEIEN",
                                    kommunenummer = "5427",
                                    postnummer = "9190",
                                    bruksenhetsnummer = null,
                                ),
                                ukjentBosted = UkjentBosted(
                                    bostedskommune = "oslo",
                                ),
                                matrikkeladresse = Matrikkeladresse(
                                    matrikkelId = null,
                                    bruksenhetsnummer = "34",
                                    tilleggsnavn = "BLABLA",
                                    postnummer = "9190",
                                    kommunenummer = "5427",
                                ),
                            ),
                        ),
                        kontaktadresse = listOf(
                            Kontaktadresse(
                                vegadresse = null,
                                postadresseIFrittFormat = PostadresseIFrittFormat(
                                    adresselinje1 = "HER ER POSTLINJE 1",
                                    adresselinje2 = "OG POSTLINJE 2",
                                    adresselinje3 = null,
                                    postnummer = "9190",
                                ),
                                postboksadresse = null,
                                utenlandskAdresse = null,
                                utenlandskAdresseIFrittFormat = null,
                            ),
                        ),
                        oppholdsadresse = listOf(
                            Oppholdsadresse(
                                vegadresse = Vegadresse(
                                    husnummer = "42",
                                    husbokstav = null,
                                    adressenavn = "SANDTAKVEIEN",
                                    kommunenummer = "5427",
                                    postnummer = "9190",
                                    bruksenhetsnummer = null,
                                ),
                                matrikkeladresse = null,
                                utenlandskAdresse = null,
                            ),
                        ),
                        statsborgerskap = listOf(
                            Statsborgerskap(
                                land = "SYR",
                                gyldigFraOgMed = null,
                                gyldigTilOgMed = null,
                            ),
                        ),
                        sivilstand = listOf(
                            SivilstandResponse(
                                type = SivilstandTyper.GIFT,
                                relatertVedSivilstand = "12345678901",
                            ),
                        ),
                        foedselsdato = emptyList(),
                        adressebeskyttelse = emptyList(),
                        vergemaalEllerFremtidsfullmakt = emptyList(),
                        doedsfall = listOf(
                            Doedsfall(
                                doedsdato = LocalDate.of(2021, 12, 21),
                            ),
                        ),
                    ),
                    hentIdenter = HentIdenter(
                        identer = listOf(
                            Id(
                                ident = "07028820547",
                                gruppe = "FOLKEREGISTERIDENT",
                                historisk = false,
                            ),
                            Id(
                                ident = "2751637578706",
                                gruppe = "AKTORID",
                                historisk = false,
                            ),
                        ),
                    ),
                ),
                errors = null,
                extensions = null,
            ).let {
                serialize(it)
            }
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
            val suksessResponseJson = PdlResponse(
                data = PersonResponseData(
                    HentPerson(
                        navn = listOf(
                            NavnResponse(
                                fornavn = "NYDELIG",
                                mellomnavn = null,
                                etternavn = "KRONJUVEL",
                                metadata = Metadata(
                                    master = "Freg",
                                    historisk = false,
                                ),
                            ),
                        ),
                        telefonnummer = emptyList(),
                        bostedsadresse = listOf(
                            Bostedsadresse(
                                vegadresse = Vegadresse(
                                    husnummer = "42",
                                    husbokstav = null,
                                    adressenavn = "SANDTAKVEIEN",
                                    kommunenummer = "5427",
                                    postnummer = "9190",
                                    bruksenhetsnummer = null,
                                ),
                                ukjentBosted = null,
                                matrikkeladresse = null,
                            ),
                        ),
                        kontaktadresse = listOf(
                            Kontaktadresse(
                                vegadresse = null,
                                postadresseIFrittFormat = PostadresseIFrittFormat(
                                    adresselinje1 = "HER ER POSTLINJE 1",
                                    adresselinje2 = "OG POSTLINJE 2",
                                    adresselinje3 = null,
                                    postnummer = "9190",
                                ),
                                postboksadresse = null,
                                utenlandskAdresse = null,
                                utenlandskAdresseIFrittFormat = null,
                            ),
                        ),
                        oppholdsadresse = listOf(
                            Oppholdsadresse(
                                vegadresse = null,
                                matrikkeladresse = Matrikkeladresse(
                                    matrikkelId = 5,
                                    bruksenhetsnummer = "H0606",
                                    tilleggsnavn = "Storgården",
                                    postnummer = "9190",
                                    kommunenummer = "5427",
                                ),
                                utenlandskAdresse = null,
                            ),
                        ),
                        statsborgerskap = listOf(
                            Statsborgerskap(
                                land = "SYR",
                                gyldigFraOgMed = null,
                                gyldigTilOgMed = null,
                            ),
                            Statsborgerskap(
                                land = "SYR",
                                gyldigFraOgMed = null,
                                gyldigTilOgMed = null,
                            ),
                        ),
                        sivilstand = listOf(
                            SivilstandResponse(
                                type = SivilstandTyper.GIFT,
                                relatertVedSivilstand = "12345678901",
                            ),
                        ),
                        foedselsdato = emptyList(),
                        adressebeskyttelse = emptyList(),
                        vergemaalEllerFremtidsfullmakt = emptyList(),
                        doedsfall = emptyList(),
                    ),
                    hentIdenter = HentIdenter(
                        identer = listOf(
                            Id(
                                ident = "07028820547",
                                gruppe = "FOLKEREGISTERIDENT",
                                historisk = false,
                            ),
                            Id(
                                ident = "2751637578706",
                                gruppe = "AKTORID",
                                historisk = false,
                            ),
                        ),
                    ),
                ),
                errors = null,
                extensions = null,
            ).let { serialize(it) }

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
            val suksessResponseJson = PdlResponse(
                data = PersonResponseData(
                    HentPerson(
                        navn = listOf(
                            NavnResponse(
                                fornavn = "NYDELIG",
                                mellomnavn = null,
                                etternavn = "KRONJUVEL",
                                metadata = Metadata(
                                    master = "Freg",
                                    historisk = false,
                                ),
                            ),
                        ),
                        telefonnummer = emptyList(),
                        bostedsadresse = emptyList(),
                        kontaktadresse = emptyList(),
                        oppholdsadresse = emptyList(),
                        statsborgerskap = emptyList(),
                        sivilstand = emptyList(),
                        foedselsdato = emptyList(),
                        adressebeskyttelse = emptyList(),
                        vergemaalEllerFremtidsfullmakt = emptyList(),
                        doedsfall = emptyList(),
                    ),
                    hentIdenter = HentIdenter(
                        identer = listOf(
                            Id(
                                ident = "07028820547",
                                gruppe = "FOLKEREGISTERIDENT",
                                historisk = false,
                            ),
                            Id(
                                ident = "2751637578706",
                                gruppe = "AKTORID",
                                historisk = false,
                            ),
                        ),
                    ),
                ),
                errors = null,
                extensions = null,
            ).let { serialize(it) }
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
            val suksessResponseJson = PdlResponse(
                data = PersonResponseData(
                    HentPerson(
                        navn = listOf(
                            NavnResponse(
                                fornavn = "NYDELIG",
                                mellomnavn = null,
                                etternavn = "KRONJUVEL",
                                metadata = Metadata(
                                    master = "Freg",
                                    historisk = false,
                                ),
                            ),
                        ),
                        telefonnummer = emptyList(),
                        bostedsadresse = emptyList(),
                        kontaktadresse = emptyList(),
                        oppholdsadresse = emptyList(),
                        statsborgerskap = emptyList(),
                        sivilstand = emptyList(),
                        foedselsdato = emptyList(),
                        adressebeskyttelse = emptyList(),
                        vergemaalEllerFremtidsfullmakt = emptyList(),
                        doedsfall = emptyList(),
                    ),
                    hentIdenter = HentIdenter(
                        identer = listOf(
                            Id(
                                ident = "07028820547",
                                gruppe = "FOLKEREGISTERIDENT",
                                historisk = false,
                            ),
                            Id(
                                ident = "2751637578706",
                                gruppe = "AKTORID",
                                historisk = false,
                            ),
                        ),
                    ),
                ),
                errors = null,
                extensions = null,
            ).let { serialize(it) }

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
            val suksessResponseJson = PdlResponse(
                data = PersonResponseData(
                    HentPerson(
                        navn = emptyList(),
                        telefonnummer = emptyList(),
                        bostedsadresse = emptyList(),
                        kontaktadresse = emptyList(),
                        oppholdsadresse = emptyList(),
                        statsborgerskap = emptyList(),
                        sivilstand = emptyList(),
                        foedselsdato = emptyList(),
                        adressebeskyttelse = emptyList(),
                        vergemaalEllerFremtidsfullmakt = emptyList(),
                        doedsfall = emptyList(),
                    ),
                    hentIdenter = HentIdenter(
                        identer = listOf(
                            Id(
                                ident = "07028820547",
                                gruppe = "FOLKEREGISTERIDENT",
                                historisk = false,
                            ),
                            Id(
                                ident = "2751637578706",
                                gruppe = "AKTORID",
                                historisk = false,
                            ),
                        ),
                    ),
                ),
                errors = null,
                extensions = null,
            ).let { serialize(it) }
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
            val suksessResponseJson = PdlResponse(
                data = PersonResponseData(
                    HentPerson(
                        navn = listOf(
                            NavnResponse(
                                fornavn = "NYDELIG",
                                mellomnavn = null,
                                etternavn = "KRONJUVEL",
                                metadata = Metadata(
                                    master = "Freg",
                                    historisk = false,
                                ),
                            ),
                        ),
                        telefonnummer = emptyList(),
                        bostedsadresse = emptyList(),
                        kontaktadresse = emptyList(),
                        oppholdsadresse = emptyList(),
                        statsborgerskap = emptyList(),
                        sivilstand = emptyList(),
                        foedselsdato = emptyList(),
                        adressebeskyttelse = emptyList(),
                        vergemaalEllerFremtidsfullmakt = emptyList(),
                        doedsfall = listOf(
                            Doedsfall(
                                doedsdato = null,
                            ),
                            Doedsfall(
                                doedsdato = LocalDate.of(2021, 12, 21),
                            ),
                        ),
                    ),
                    hentIdenter = HentIdenter(
                        identer = listOf(
                            Id(
                                ident = "07028820547",
                                gruppe = "FOLKEREGISTERIDENT",
                                historisk = false,
                            ),
                            Id(
                                ident = "2751637578706",
                                gruppe = "AKTORID",
                                historisk = false,
                            ),
                        ),
                    ),
                ),
                errors = null,
                extensions = null,
            ).let { serialize(it) }
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
