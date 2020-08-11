package no.nav.su.se.bakover.web.routes

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import io.kotest.matchers.shouldBe
import io.ktor.http.HttpMethod.Companion.Get
import io.ktor.http.HttpStatusCode
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.http.HttpStatusCode.Companion.Unauthorized
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.withTestApplication
import no.nav.su.se.bakover.client.ClientError
import no.nav.su.se.bakover.client.person.PersonOppslag
import no.nav.su.se.bakover.database.DatabaseBuilder
import no.nav.su.se.bakover.database.EmbeddedDatabase
import no.nav.su.se.bakover.domain.AktørId
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.Person
import no.nav.su.se.bakover.domain.Person.Adresse
import no.nav.su.se.bakover.domain.Person.Navn
import no.nav.su.se.bakover.domain.Telefonnummer
import no.nav.su.se.bakover.web.buildClients
import no.nav.su.se.bakover.web.defaultRequest
import no.nav.su.se.bakover.web.testEnv
import no.nav.su.se.bakover.web.testSusebakover
import org.junit.jupiter.api.Test
import org.skyscreamer.jsonassert.JSONAssert
import kotlin.test.assertEquals

internal class PersonRoutesKtTest {
    private val sakRepo = DatabaseBuilder.build(EmbeddedDatabase.instance())
    private val errorMessage = "beklager, det gikk dårlig"

    @Test
    fun `får ikke hente persondata uten å være innlogget`() {
        withTestApplication({
            testEnv()
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
            testEnv()
            testSusebakover()
        }) {
            defaultRequest(Get, "$personPath/qwertyuiopå")
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
                    "navn": {
                        "fornavn": "Tore",
                        "mellomnavn": "Johnas",
                        "etternavn": "Strømøy"
                    },
                    "telefonnummer": {
                        "landskode": "47",
                        "nummer": "12345678"
                    },
                    "adresse": {
                        "adressenavn": "Oslogata",
                        "husnummer": "12",
                        "husbokstav": null,
                        "postnummer": "0050",
                        "poststed": "Oslo",
                        "bruksenhet": "U1H20",
                        "kommunenavn": "Oslo",
                        "kommunenummer":"0301"
                    },
                    "statsborgerskap": "NOR",
                    "kjønn": "MANN"
                }
            """.trimIndent()

        withTestApplication({
            testEnv()
            testSusebakover(httpClients = buildClients(personOppslag = personoppslag(200, testIdent)))
        }) {
            defaultRequest(Get, "$personPath/$testIdent")
        }.apply {
            assertEquals(OK, response.status())
            JSONAssert.assertEquals(excpectedResponseJson, response.content!!, true)
        }
    }

    @Test
    fun `skal propagere httpStatus fra PDL kall`() {
        val testIdent = "12345678910"

        withTestApplication({
            testEnv()
            testSusebakover(httpClients = buildClients(personOppslag = personoppslag(Unauthorized.value, null)))
        }) {
            defaultRequest(Get, "$personPath/$testIdent")
        }.apply {
            assertEquals(Unauthorized, response.status())
            response.content shouldBe errorMessage
        }
    }

    private fun personoppslag(statusCode: Int, testIdent: String?) = object :
        PersonOppslag {
        override fun person(fnr: Fnr): Either<ClientError, Person> = when (testIdent) {
            fnr.toString() -> Person(
                fnr = Fnr("12345678910"),
                aktørId = AktørId("2437280977705"),
                navn = Navn(
                    fornavn = "Tore",
                    mellomnavn = "Johnas",
                    etternavn = "Strømøy"
                ),
                telefonnummer = Telefonnummer(landskode = "47", nummer = "12345678"),
                adresse = Adresse(
                    adressenavn = "Oslogata",
                    husnummer = "12",
                    husbokstav = null,
                    postnummer = "0050",
                    poststed = "Oslo",
                    bruksenhet = "U1H20",
                    kommunenavn = "Oslo",
                    kommunenummer = "0301"
                ),
                statsborgerskap = "NOR",
                kjønn = "MANN"

            ).right()
            else -> ClientError(statusCode, "beklager, det gikk dårlig").left()
        }

        override fun aktørId(fnr: Fnr): Either<ClientError, AktørId> = throw NotImplementedError()
    }
}
