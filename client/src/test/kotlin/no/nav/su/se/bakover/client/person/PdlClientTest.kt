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
import no.nav.su.se.bakover.common.domain.sak.Sakstype
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
import person.domain.BorPåAdresse
import person.domain.BorPåAdresseRequest
import person.domain.Identifikator
import person.domain.KunneIkkeHenteBorPåAdresse
import person.domain.KunneIkkeHentePerson
import person.domain.PersonPåAdresse
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
        sivilstand = SivilstandResponse(
            type = SivilstandTyper.GIFT,
            relatertVedSivilstand = "12345678901",
        ),
        fødsel = null,
        adressebeskyttelse = null,
        vergemålEllerFremtidsfullmakt = false,
        dødsdato = 21.desember(2021),
        dødsbo = emptyList(),
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
                Sakstype.UFØRE,
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
                Sakstype.UFØRE,
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
                Sakstype.UFØRE,
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
                Sakstype.UFØRE,
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
                Sakstype.UFØRE,
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
                JwtToken.BrukerToken("ignored fordi mock"),
                Sakstype.UFØRE,
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
                                gyldigFraOgMed = null,
                                gyldigTilOgMed = null,
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
                        kontaktinformasjonForDoedsbo = emptyList(),
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
                Sakstype.UFØRE,
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
                                gyldigFraOgMed = null,
                                gyldigTilOgMed = null,
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
                        kontaktinformasjonForDoedsbo = emptyList(),
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
                Sakstype.UFØRE,
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
                                gyldigFraOgMed = null,
                                gyldigTilOgMed = null,
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
                        kontaktinformasjonForDoedsbo = emptyList(),
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
                Sakstype.UFØRE,
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
                        kontaktinformasjonForDoedsbo = emptyList(),
                        oppholdsadresse = emptyList(),
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
            client.personForSystembruker(Fnr("07028820547"), Sakstype.UFØRE) shouldBe expectedPdlDataTemplate.copy(
                adresse = emptyList(),
                sivilstand = null,
                dødsdato = null,
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
                        kontaktinformasjonForDoedsbo = emptyList(),
                        oppholdsadresse = emptyList(),
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
                Sakstype.UFØRE,
            ) shouldBe expectedPdlDataTemplate.copy(
                adresse = emptyList(),
                sivilstand = null,
                dødsdato = null,
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
                        kontaktinformasjonForDoedsbo = emptyList(),
                        oppholdsadresse = emptyList(),
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
            client.personForSystembruker(
                Fnr("07028820547"),
                Sakstype.UFØRE,
            ) shouldBe KunneIkkeHentePerson.FantIkkePerson.left()
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
                        kontaktinformasjonForDoedsbo = emptyList(),
                        oppholdsadresse = emptyList(),
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
            client.personForSystembruker(Fnr("07028820547"), Sakstype.UFØRE) shouldBe expectedPdlDataTemplate.copy(
                adresse = emptyList(),
                sivilstand = null,
            ).right()
        }
    }

    @Test
    fun `henter dødsbo dersom kontaktinformasjonForDoedsbo ikke er tom`() {
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
                        kontaktinformasjonForDoedsbo = listOf(
                            KontaktinformasjonForDoedsbo(
                                skifteform = KontaktinformasjonForDoedsboSkifteform.OFFENTLIG,
                                attestutstedelsesdato = LocalDate.of(2021, 12, 25),
                                personSomKontakt = KontaktinformasjonForDoedsboPersonSomKontakt(
                                    foedselsdato = LocalDate.of(1980, 1, 1),
                                    personnavn = KontaktinformasjonForDoedsboPersonnavn(
                                        fornavn = "KONTAKT",
                                        mellomnavn = null,
                                        etternavn = "PERSON",
                                    ),
                                    identifikasjonsnummer = "11111111111",
                                ),
                                advokatSomKontakt = KontaktinformasjonForDoedsboAdvokatSomKontakt(
                                    personnavn = KontaktinformasjonForDoedsboPersonnavn(
                                        fornavn = "ADVOKAT",
                                        mellomnavn = "M",
                                        etternavn = "ANSEN",
                                    ),
                                    organisasjonsnavn = "Advokatfirmaet AS",
                                    organisasjonsnummer = "999888777",
                                ),
                                organisasjonSomKontakt = KontaktinformasjonForDoedsboOrganisasjonSomKontakt(
                                    kontaktperson = KontaktinformasjonForDoedsboPersonnavn(
                                        fornavn = "ORG",
                                        mellomnavn = null,
                                        etternavn = "KONTAKT",
                                    ),
                                    organisasjonsnavn = "Organisasjonen AS",
                                    organisasjonsnummer = "888777666",
                                ),
                                adresse = KontaktinformasjonForDoedsboAdresse(
                                    adresselinje1 = "Testveien 1",
                                    adresselinje2 = "Etasje 3",
                                    poststedsnavn = "OSLO",
                                    postnummer = "0010",
                                    landkode = "NO",
                                ),
                            ),
                        ),
                        oppholdsadresse = emptyList(),
                        sivilstand = emptyList(),
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
            client.personForSystembruker(Fnr("07028820547"), Sakstype.UFØRE) shouldBe expectedPdlDataTemplate.copy(
                adresse = emptyList(),
                sivilstand = null,
                dødsbo = listOf(
                    Dødsbo(
                        kontaktPerson = Dødsbo.Kontaktinformasjon(
                            fornavn = "KONTAKT",
                            mellomnavn = null,
                            etternavn = "PERSON",
                            identifikasjonsnummer = "11111111111",
                            organisasjonsnavn = null,
                            organisasjonsnummer = null,
                        ),
                        kontaktAdvokat = Dødsbo.Kontaktinformasjon(
                            fornavn = "ADVOKAT",
                            mellomnavn = "M",
                            etternavn = "ANSEN",
                            identifikasjonsnummer = null,
                            organisasjonsnavn = "Advokatfirmaet AS",
                            organisasjonsnummer = "999888777",
                        ),
                        kontaktOrganisasjon = Dødsbo.Kontaktinformasjon(
                            fornavn = "ORG",
                            mellomnavn = null,
                            etternavn = "KONTAKT",
                            identifikasjonsnummer = null,
                            organisasjonsnavn = "Organisasjonen AS",
                            organisasjonsnummer = "888777666",
                        ),
                        adresselinje1 = "Testveien 1",
                        adresselinje2 = "Etasje 3",
                        poststedsnavn = "OSLO",
                        postnummer = "0010",
                        landkode = "NO",
                    ),
                ),
            ).right()
        }
    }

    @Test
    fun `henter borPåAdresse OK med folkeregisteridentifikator som ikke er I_BRUK filtreres vekk`() {
        startedWireMockServerWithCorrelationId {
            val suksessResponseJson = PdlResponse(
                data = BorPåAdresseResponse(
                    sokPerson = BorPåAdresseResponseData(
                        hits = listOf(
                            BorPåAdressePersonResponse(
                                person = BorPåAdressePerson(
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
                                            gyldigFraOgMed = "2026-01-01T00:00",
                                            gyldigTilOgMed = null,
                                        ),
                                    ),
                                    folkeregisteridentifikator = listOf(
                                        Folkeregisteridentifikator(
                                            identifikasjonsnummer = "07028820547",
                                            status = "I_BRUK",
                                            type = "FOLKEREGISTERIDENT",
                                        ),
                                        Folkeregisteridentifikator(
                                            identifikasjonsnummer = "07028820547",
                                            status = "IKKE_I_BRUK",
                                            type = "FOLKEREGISTERIDENT",
                                        ),
                                    ),
                                ),
                            ),
                        ),
                        totalHits = 1,
                        pageNumber = 1,
                        totalPages = 1,
                    ),
                ),
                errors = null,
                extensions = null,
            ).let { serialize(it) }
            val token = "etOnBehalfOfToken"
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
            client.borPåAdresse(
                BorPåAdresseRequest(
                    adressenavn = "SANDTAKVEIEN",
                    husnummer = "42",
                    postnummer = "9190",
                    bruksenhetsnummer = "",
                ),
                JwtToken.BrukerToken("ignored because of mock"),
                Sakstype.UFØRE,
            ) shouldBe BorPåAdresse(
                søktAdresse = "SANDTAKVEIEN 42, 9190",
                treff = listOf(
                    PersonPåAdresse(
                        fornavn = "NYDELIG",
                        etternavn = "KRONJUVEL",
                        mellomnavn = "",
                        adressenavn = "SANDTAKVEIEN",
                        husnummer = "42",
                        husbokstav = "",
                        postnummer = "9190",
                        bruksenhetsnummer = "",
                        gyldigFraOgMed = LocalDate.of(2026, 1, 1),
                        gyldigTilOgMed = null,
                        folkeregisteridentifikator = listOf(
                            Identifikator(
                                ident = "07028820547",
                                type = "FOLKEREGISTERIDENT",
                            ),
                        ),
                    ),
                ),
            ).right()
        }
    }

    @Test
    fun `henter borPåAdresse og fjerner personer som ikke lenger på bor adressen`() {
        startedWireMockServerWithCorrelationId {
            val suksessResponseJson = PdlResponse(
                data = BorPåAdresseResponse(
                    sokPerson = BorPåAdresseResponseData(
                        hits = listOf(
                            BorPåAdressePersonResponse(
                                person = BorPåAdressePerson(
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
                                            gyldigFraOgMed = "2026-01-01T00:00",
                                            gyldigTilOgMed = null,
                                        ),
                                    ),
                                    folkeregisteridentifikator = listOf(
                                        Folkeregisteridentifikator(
                                            identifikasjonsnummer = "07028820547",
                                            status = "I_BRUK",
                                            type = "FOLKEREGISTERIDENT",
                                        ),
                                    ),
                                ),
                            ),
                            BorPåAdressePersonResponse(
                                person = BorPåAdressePerson(
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
                                    bostedsadresse = listOf(
                                        Bostedsadresse(
                                            vegadresse = Vegadresse(
                                                husnummer = "42",
                                                husbokstav = null,
                                                adressenavn = "NOE ANNET",
                                                kommunenummer = "5427",
                                                postnummer = "9190",
                                                bruksenhetsnummer = null,
                                            ),
                                            ukjentBosted = null,
                                            matrikkeladresse = null,
                                            gyldigFraOgMed = "2026-01-01T00:00",
                                            gyldigTilOgMed = null,
                                        ),
                                    ),
                                    folkeregisteridentifikator = listOf(
                                        Folkeregisteridentifikator(
                                            identifikasjonsnummer = "07028820547",
                                            status = "I_BRUK",
                                            type = "FOLKEREGISTERIDENT",
                                        ),
                                    ),
                                ),
                            ),
                        ),
                        totalHits = 2,
                        pageNumber = 1,
                        totalPages = 1,
                    ),
                ),
                errors = null,
                extensions = null,
            ).let { serialize(it) }
            val token = "etOnBehalfOfToken"
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
            client.borPåAdresse(
                BorPåAdresseRequest(
                    adressenavn = "SANDTAKVEIEN",
                    husnummer = "42",
                    postnummer = "9190",
                    bruksenhetsnummer = "",
                ),
                JwtToken.BrukerToken("ignored because of mock"),
                Sakstype.UFØRE,
            ) shouldBe BorPåAdresse(
                søktAdresse = "SANDTAKVEIEN 42, 9190",
                treff = listOf(
                    PersonPåAdresse(
                        fornavn = "NYDELIG",
                        etternavn = "KRONJUVEL",
                        mellomnavn = "",
                        adressenavn = "SANDTAKVEIEN",
                        husnummer = "42",
                        husbokstav = "",
                        postnummer = "9190",
                        bruksenhetsnummer = "",
                        gyldigFraOgMed = LocalDate.of(2026, 1, 1),
                        gyldigTilOgMed = null,
                        folkeregisteridentifikator = listOf(
                            Identifikator(
                                ident = "07028820547",
                                type = "FOLKEREGISTERIDENT",
                            ),
                        ),
                    ),
                ),
            ).right()
        }
    }

    @Test
    fun `henter borPåAdresse og fjerner personer som ikke har samme bruksenhetsnummer`() {
        startedWireMockServerWithCorrelationId {
            val suksessResponseJson = PdlResponse(
                data = BorPåAdresseResponse(
                    sokPerson = BorPåAdresseResponseData(
                        hits = listOf(
                            BorPåAdressePersonResponse(
                                person = BorPåAdressePerson(
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
                                    bostedsadresse = listOf(
                                        Bostedsadresse(
                                            vegadresse = Vegadresse(
                                                husnummer = "42",
                                                husbokstav = null,
                                                adressenavn = "SANDTAKVEIEN",
                                                kommunenummer = "5427",
                                                postnummer = "9190",
                                                bruksenhetsnummer = "h101",
                                            ),
                                            ukjentBosted = null,
                                            matrikkeladresse = null,
                                            gyldigFraOgMed = "2026-01-01T00:00",
                                            gyldigTilOgMed = null,
                                        ),
                                    ),
                                    folkeregisteridentifikator = listOf(
                                        Folkeregisteridentifikator(
                                            identifikasjonsnummer = "07028820547",
                                            status = "I_BRUK",
                                            type = "FOLKEREGISTERIDENT",
                                        ),
                                    ),
                                ),
                            ),
                            BorPåAdressePersonResponse(
                                person = BorPåAdressePerson(
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
                                    bostedsadresse = listOf(
                                        Bostedsadresse(
                                            vegadresse = Vegadresse(
                                                husnummer = "42",
                                                husbokstav = null,
                                                adressenavn = "SANDTAKVEIEN",
                                                kommunenummer = "5427",
                                                postnummer = "9190",
                                                bruksenhetsnummer = "h102",
                                            ),
                                            ukjentBosted = null,
                                            matrikkeladresse = null,
                                            gyldigFraOgMed = "2026-01-01T00:00",
                                            gyldigTilOgMed = null,
                                        ),
                                    ),
                                    folkeregisteridentifikator = listOf(
                                        Folkeregisteridentifikator(
                                            identifikasjonsnummer = "07028820547",
                                            status = "I_BRUK",
                                            type = "FOLKEREGISTERIDENT",
                                        ),
                                    ),
                                ),
                            ),
                        ),
                        totalHits = 2,
                        pageNumber = 1,
                        totalPages = 1,
                    ),
                ),
                errors = null,
                extensions = null,
            ).let { serialize(it) }
            val token = "etOnBehalfOfToken"
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
            client.borPåAdresse(
                BorPåAdresseRequest(
                    adressenavn = "SANDTAKVEIEN",
                    husnummer = "42",
                    postnummer = "9190",
                    bruksenhetsnummer = "h101",
                ),
                JwtToken.BrukerToken("ignored because of mock"),
                Sakstype.UFØRE,
            ) shouldBe BorPåAdresse(
                søktAdresse = "SANDTAKVEIEN 42, 9190",
                treff = listOf(
                    PersonPåAdresse(
                        fornavn = "NYDELIG",
                        etternavn = "KRONJUVEL",
                        mellomnavn = "",
                        adressenavn = "SANDTAKVEIEN",
                        husnummer = "42",
                        husbokstav = "",
                        postnummer = "9190",
                        bruksenhetsnummer = "h101",
                        gyldigFraOgMed = LocalDate.of(2026, 1, 1),
                        gyldigTilOgMed = null,
                        folkeregisteridentifikator = listOf(
                            Identifikator(
                                ident = "07028820547",
                                type = "FOLKEREGISTERIDENT",
                            ),
                        ),
                    ),
                ),
            ).right()
        }
    }

    @Test
    fun `borPåAdresse med flere sider går OK`() {
        startedWireMockServerWithCorrelationId {
            val side1ResponseJson = PdlResponse(
                data = BorPåAdresseResponse(
                    sokPerson = BorPåAdresseResponseData(
                        hits = listOf(
                            BorPåAdressePersonResponse(
                                person = BorPåAdressePerson(
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
                                            gyldigFraOgMed = "2026-01-01T00:00",
                                            gyldigTilOgMed = null,
                                        ),
                                    ),
                                    folkeregisteridentifikator = listOf(
                                        Folkeregisteridentifikator(
                                            identifikasjonsnummer = "07028820547",
                                            status = "I_BRUK",
                                            type = "FOLKEREGISTERIDENT",
                                        ),
                                    ),
                                ),
                            ),
                        ),
                        totalHits = 2,
                        pageNumber = 1,
                        totalPages = 2,
                    ),
                ),
                errors = null,
                extensions = null,
            ).let { serialize(it) }
            val side2ResponseJson = PdlResponse(
                data = BorPåAdresseResponse(
                    sokPerson = BorPåAdresseResponseData(
                        hits = listOf(
                            BorPåAdressePersonResponse(
                                person = BorPåAdressePerson(
                                    navn = listOf(
                                        NavnResponse(
                                            fornavn = "STRÅLENDE",
                                            mellomnavn = null,
                                            etternavn = "PRAKTSTYKKE",
                                            metadata = Metadata(
                                                master = "Freg",
                                                historisk = false,
                                            ),
                                        ),
                                    ),
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
                                            gyldigFraOgMed = "2026-01-01T00:00",
                                            gyldigTilOgMed = null,
                                        ),
                                    ),
                                    folkeregisteridentifikator = listOf(
                                        Folkeregisteridentifikator(
                                            identifikasjonsnummer = "07028820548",
                                            status = "I_BRUK",
                                            type = "FOLKEREGISTERIDENT",
                                        ),
                                    ),
                                ),
                            ),
                        ),
                        totalHits = 2,
                        pageNumber = 2,
                        totalPages = 2,
                    ),
                ),
                errors = null,
                extensions = null,
            ).let { serialize(it) }
            val token = "etOnBehalfOfToken"
            val azureAdMock = mock<AzureAd> {
                on { onBehalfOfToken(any(), any()) } doReturn token
            }

            stubFor(
                wiremockBuilderOnBehalfOf("Bearer $token")
                    .withRequestBody(WireMock.containing("\"pageNumber\":1"))
                    .willReturn(WireMock.ok(side1ResponseJson)),
            )
            stubFor(
                wiremockBuilderOnBehalfOf("Bearer $token")
                    .withRequestBody(WireMock.containing("\"pageNumber\":2"))
                    .willReturn(WireMock.ok(side2ResponseJson)),
            )

            val client = PdlClient(
                PdlClientConfig(
                    vars = ApplicationConfig.ClientsConfig.PdlConfig(baseUrl(), "clientId"),
                    azureAd = azureAdMock,
                ),
            )
            client.borPåAdresse(
                BorPåAdresseRequest(
                    adressenavn = "SANDTAKVEIEN",
                    husnummer = "42",
                    postnummer = "9190",
                    bruksenhetsnummer = "",
                ),
                JwtToken.BrukerToken("ignored because of mock"),
                Sakstype.UFØRE,
            ) shouldBe BorPåAdresse(
                søktAdresse = "SANDTAKVEIEN 42, 9190",
                treff = listOf(
                    PersonPåAdresse(
                        fornavn = "NYDELIG",
                        etternavn = "KRONJUVEL",
                        mellomnavn = "",
                        adressenavn = "SANDTAKVEIEN",
                        husnummer = "42",
                        husbokstav = "",
                        postnummer = "9190",
                        bruksenhetsnummer = "",
                        gyldigFraOgMed = LocalDate.of(2026, 1, 1),
                        gyldigTilOgMed = null,
                        folkeregisteridentifikator = listOf(
                            Identifikator(
                                ident = "07028820547",
                                type = "FOLKEREGISTERIDENT",
                            ),
                        ),
                    ),
                    PersonPåAdresse(
                        fornavn = "STRÅLENDE",
                        etternavn = "PRAKTSTYKKE",
                        mellomnavn = "",
                        adressenavn = "SANDTAKVEIEN",
                        husnummer = "42",
                        husbokstav = "",
                        postnummer = "9190",
                        bruksenhetsnummer = "",
                        gyldigFraOgMed = LocalDate.of(2026, 1, 1),
                        gyldigTilOgMed = null,
                        folkeregisteridentifikator = listOf(
                            Identifikator(
                                ident = "07028820548",
                                type = "FOLKEREGISTERIDENT",
                            ),
                        ),
                    ),
                ),
            ).right()
        }
    }

    @Test
    fun `borPåAdresse med flere sider hvor side 2 feiler og stopper while og returnerer feiltype`() {
        startedWireMockServerWithCorrelationId {
            val side1ResponseJson = PdlResponse(
                data = BorPåAdresseResponse(
                    sokPerson = BorPåAdresseResponseData(
                        hits = listOf(
                            BorPåAdressePersonResponse(
                                person = BorPåAdressePerson(
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
                                            gyldigFraOgMed = "2026-01-01T00:00",
                                            gyldigTilOgMed = null,
                                        ),
                                    ),
                                    folkeregisteridentifikator = listOf(
                                        Folkeregisteridentifikator(
                                            identifikasjonsnummer = "07028820547",
                                            status = "I_BRUK",
                                            type = "FOLKEREGISTERIDENT",
                                        ),
                                    ),
                                ),
                            ),
                        ),
                        totalHits = 2,
                        pageNumber = 1,
                        totalPages = 2,
                    ),
                ),
                errors = null,
                extensions = null,
            ).let { serialize(it) }
            val side2ResponseJson = PdlResponse(
                data = BorPåAdresseResponse(
                    sokPerson = BorPåAdresseResponseData(
                        hits = emptyList(),
                        totalHits = 2,
                        pageNumber = 2,
                        totalPages = 2,
                    ),
                ),
                errors = listOf(
                    PdlError(
                        message = "Ikke tilgang",
                        path = listOf("sokPerson"),
                        extensions = PdlErrorExtension(
                            code = "unauthorized",
                        ),
                    ),
                ),
                extensions = null,
            ).let { serialize(it) }
            val token = "etOnBehalfOfToken"
            val azureAdMock = mock<AzureAd> {
                on { onBehalfOfToken(any(), any()) } doReturn token
            }

            stubFor(
                wiremockBuilderOnBehalfOf("Bearer $token")
                    .withRequestBody(WireMock.containing("\"pageNumber\":1"))
                    .willReturn(WireMock.ok(side1ResponseJson)),
            )
            stubFor(
                wiremockBuilderOnBehalfOf("Bearer $token")
                    .withRequestBody(WireMock.containing("\"pageNumber\":2"))
                    .willReturn(WireMock.ok(side2ResponseJson)),
            )

            val client = PdlClient(
                PdlClientConfig(
                    vars = ApplicationConfig.ClientsConfig.PdlConfig(baseUrl(), "clientId"),
                    azureAd = azureAdMock,
                ),
            )
            client.borPåAdresse(
                BorPåAdresseRequest(
                    adressenavn = "SANDTAKVEIEN",
                    husnummer = "42",
                    postnummer = "9190",
                    bruksenhetsnummer = "",
                ),
                JwtToken.BrukerToken("ignored because of mock"),
                Sakstype.UFØRE,
            ) shouldBe KunneIkkeHenteBorPåAdresse.IkkeTilgangTilPerson.left()

            // Bekrefter at while-løkken stoppet etter side 2 og ikke forsøkte side 3
            verify(
                2,
                WireMock.postRequestedFor(WireMock.urlPathEqualTo("/graphql"))
                    .withHeader("Authorization", WireMock.equalTo("Bearer $token"))
                    .withHeader("Tema", WireMock.equalTo("SUP")),
            )
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
