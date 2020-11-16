package no.nav.su.se.bakover.web.routes

import arrow.core.Either
import arrow.core.left
import io.kotest.matchers.shouldBe
import io.ktor.http.HttpMethod.Companion.Get
import io.ktor.http.HttpStatusCode
import io.ktor.http.HttpStatusCode.Companion.NotFound
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.withTestApplication
import no.nav.su.se.bakover.client.stubs.person.PersonOppslagStub
import no.nav.su.se.bakover.domain.AktørId
import no.nav.su.se.bakover.domain.Brukerrolle
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.Person
import no.nav.su.se.bakover.domain.person.PersonOppslag
import no.nav.su.se.bakover.domain.person.PersonOppslag.KunneIkkeHentePerson
import no.nav.su.se.bakover.web.TestClientsBuilder.testClients
import no.nav.su.se.bakover.web.defaultRequest
import no.nav.su.se.bakover.web.testSusebakover
import org.junit.jupiter.api.Test
import org.skyscreamer.jsonassert.JSONAssert
import kotlin.test.assertEquals

internal class PersonRoutesKtTest {
    @Test
    fun `får ikke hente persondata uten å være innlogget`() {
        withTestApplication({
            testSusebakover()
        }) {
            handleRequest(Get, "$personPath/12345678910")
        }.apply {
            assertEquals(HttpStatusCode.Unauthorized, response.status())
        }
    }

    @Test
    fun `bad request ved ugyldig fnr`() {
        withTestApplication({
            testSusebakover()
        }) {
            defaultRequest(Get, "$personPath/qwertyuiopå", listOf(Brukerrolle.Veileder))
        }.apply {
            assertEquals(HttpStatusCode.BadRequest, response.status())
        }
    }

    @Test
    fun `kan hente data gjennom PersonOppslag`() {
        val testIdent = "12345678910"

        //language=JSON
        val excpectedResponseJson =
            """
                {
                    "fnr": "12345678910",
                    "aktorId": "2437280977705",
                    "fornavn": "Tore",
                    "mellomnavn": "Johnas",
                    "etternavn": "Strømøy",
                    "navn": {
                        "fornavn": "Tore",
                        "mellomnavn": "Johnas",
                        "etternavn": "Strømøy"
                    },
                    "telefonnummer": {
                        "landskode": "+47",
                        "nummer": "12345678"
                    },
                    "adresse": {
                        "adressenavn": "Oslogata",
                        "husnummer": "12",
                        "husbokstav": null,
                        "postnummer": "0050",
                        "poststed": "OSLO",
                        "bruksenhet": "U1H20",
                        "kommunenavn": "OSLO",
                        "kommunenummer":"0301"
                    },
                    "statsborgerskap": "NOR",
                    "kjønn": "MANN",
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
            testSusebakover(clients = testClients.copy(personOppslag = personoppslag(testIdent = testIdent)))
        }) {
            defaultRequest(Get, "$personPath/$testIdent", listOf(Brukerrolle.Veileder))
        }.apply {
            assertEquals(OK, response.status())
            JSONAssert.assertEquals(excpectedResponseJson, response.content!!, true)
        }
    }

    @Test
    fun `skal svare med 500 hvis ukjent feil`() {
        val testIdent = "12345678910"

        withTestApplication({
            testSusebakover(clients = testClients.copy(personOppslag = personoppslag(KunneIkkeHentePerson.Ukjent, null)))
        }) {
            defaultRequest(Get, "$personPath/$testIdent", listOf(Brukerrolle.Veileder))
        }.apply {
            response.status() shouldBe HttpStatusCode.InternalServerError
        }
    }

    @Test
    fun `skal svare med 404 hvis person ikke funnet`() {
        val testIdent = "12345678910"

        withTestApplication({
            testSusebakover(clients = testClients.copy(personOppslag = personoppslag(KunneIkkeHentePerson.FantIkkePerson, null)))
        }) {
            defaultRequest(Get, "$personPath/$testIdent", listOf(Brukerrolle.Veileder))
        }.apply {
            response.status() shouldBe NotFound
        }
    }

    private fun personoppslag(feil: KunneIkkeHentePerson = KunneIkkeHentePerson.Ukjent, testIdent: String?) = object : PersonOppslag {
        override fun person(fnr: Fnr): Either<KunneIkkeHentePerson, Person> = when (testIdent) {
            fnr.toString() -> PersonOppslagStub.person(fnr)
            else -> feil.left()
        }

        override fun aktørId(fnr: Fnr): Either<KunneIkkeHentePerson, AktørId> = throw NotImplementedError()
    }
}
