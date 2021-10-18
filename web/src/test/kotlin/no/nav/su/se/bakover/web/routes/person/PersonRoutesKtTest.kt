package no.nav.su.se.bakover.web.routes.person

import arrow.core.left
import arrow.core.right
import io.kotest.matchers.shouldBe
import io.ktor.http.HttpMethod
import io.ktor.http.HttpMethod.Companion.Get
import io.ktor.http.HttpStatusCode
import io.ktor.http.HttpStatusCode.Companion.NotFound
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.setBody
import io.ktor.server.testing.withTestApplication
import no.nav.su.se.bakover.client.stubs.person.PersonOppslagStub
import no.nav.su.se.bakover.domain.Brukerrolle
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.person.KunneIkkeHentePerson
import no.nav.su.se.bakover.service.AccessCheckProxy
import no.nav.su.se.bakover.service.person.PersonService
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.web.TestServicesBuilder
import no.nav.su.se.bakover.web.defaultRequest
import no.nav.su.se.bakover.web.testSusebakover
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.skyscreamer.jsonassert.JSONAssert

internal class PersonRoutesKtTest {

    private val testIdent = "12345678910"
    private val person = PersonOppslagStub.nyTestPerson(Fnr(testIdent))

    private val services = TestServicesBuilder.services()

    @Test
    fun `får ikke hente persondata uten å være innlogget`() {
        withTestApplication({
            testSusebakover()
        }) {
            handleRequest(HttpMethod.Post, "$personPath/søk") {
                setBody("""{"fnr":"$testIdent"}""")
            }
        }.apply {
            response.status() shouldBe HttpStatusCode.Unauthorized
        }
    }

    @Test
    fun `bad request ved ugyldig fnr`() {
        withTestApplication({
            testSusebakover()
        }) {
            defaultRequest(HttpMethod.Post, "$personPath/søk", listOf(Brukerrolle.Veileder)) {
                setBody("""{"fnr":"qwertyuiopå"}""")
            }
        }.apply {
            response.status() shouldBe HttpStatusCode.BadRequest
        }
    }

    @Test
    fun `kan hente data gjennom PersonOppslag`() {
        val personServiceMock = mock<PersonService> { on { hentPerson(any()) } doReturn person.right() }
        val accessCheckProxyMock = mock<AccessCheckProxy> { on { proxy() } doReturn services.copy(person = personServiceMock) }

        //language=JSON
        val expectedResponseJson =
            """
                {
                    "fnr": "$testIdent",
                    "aktorId": "2437280977705",
                    "navn": {
                        "fornavn": "Tore",
                        "mellomnavn": "Johnas",
                        "etternavn": "Strømøy"
                    },
                    "telefonnummer": {
                        "landskode": "+47",
                        "nummer": "12345678"
                    },
                    "adresse": [{
                        "adresselinje": "Oslogata 12",
                        "postnummer": "0050",
                        "poststed": "OSLO",
                        "bruksenhet": "U1H20",
                        "kommunenavn": "OSLO",
                        "kommunenummer":"0301",
                        "adressetype": "Bostedsadresse",
                        "adresseformat": "Vegadresse"
                    }],
                    "sivilstand": {
                    "type": "GIFT",
                    "relatertVedSivilstand": "15116414950"
                    },
                    "statsborgerskap": "NOR",
                    "kjønn": "MANN",
                    "fødselsdato": "1990-01-01",
                    "alder": 31,
                    "adressebeskyttelse": null,
                    "skjermet": false,
                    "kontaktinfo": {
                        "epostadresse": "mail@epost.com",
                        "mobiltelefonnummer": "90909090",
                        "reservert": false,
                        "kanVarsles": true,
                        "språk": "nb"
                    },
                    "fullmakt": null,
                    "vergemål": null
                }
            """.trimIndent()

        withTestApplication({
            testSusebakover(accessCheckProxy = accessCheckProxyMock, clock = fixedClock)
        }) {
            defaultRequest(HttpMethod.Post, "$personPath/søk", listOf(Brukerrolle.Veileder)) {
                setBody("""{"fnr":"$testIdent"}""")
            }
        }.apply {
            response.status() shouldBe OK
            JSONAssert.assertEquals(expectedResponseJson, response.content!!, true)
        }
    }

    @Test
    fun `skal svare med 500 hvis ukjent feil`() {
        val personServiceMock = mock<PersonService> { on { hentPerson(any()) } doReturn KunneIkkeHentePerson.Ukjent.left() }
        val accessCheckProxyMock = mock<AccessCheckProxy> { on { proxy() } doReturn services.copy(person = personServiceMock) }

        withTestApplication({
            testSusebakover(accessCheckProxy = accessCheckProxyMock)
        }) {
            defaultRequest(HttpMethod.Post, "$personPath/søk", listOf(Brukerrolle.Veileder)) {
                setBody("""{"fnr":"$testIdent"}""")
            }
        }.apply {
            response.status() shouldBe HttpStatusCode.InternalServerError
        }
    }

    @Test
    fun `skal svare med 404 hvis person ikke funnet`() {
        val personServiceMock = mock<PersonService> { on { hentPerson(any()) } doReturn KunneIkkeHentePerson.FantIkkePerson.left() }
        val accessCheckProxyMock = mock<AccessCheckProxy> { on { proxy() } doReturn services.copy(person = personServiceMock) }

        withTestApplication({
            testSusebakover(accessCheckProxy = accessCheckProxyMock)
        }) {
            defaultRequest(Get, "$personPath/$testIdent", listOf(Brukerrolle.Veileder))
        }.apply {
            response.status() shouldBe NotFound
        }
    }
}
