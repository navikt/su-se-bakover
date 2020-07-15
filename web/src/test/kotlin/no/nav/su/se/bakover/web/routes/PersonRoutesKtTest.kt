package no.nav.su.se.bakover.web.routes

import io.ktor.http.HttpMethod.Companion.Get
import io.ktor.http.HttpStatusCode
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.http.HttpStatusCode.Companion.Unauthorized
import io.ktor.locations.KtorExperimentalLocationsAPI
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.withTestApplication
import io.ktor.util.KtorExperimentalAPI
import kotlin.test.assertEquals
import no.nav.su.se.bakover.client.ClientResponse
import no.nav.su.se.bakover.client.person.PersonOppslag
import no.nav.su.se.bakover.database.DatabaseBuilder
import no.nav.su.se.bakover.database.EmbeddedDatabase
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.web.*
import no.nav.su.se.bakover.web.buildHttpClients
import no.nav.su.se.bakover.web.testEnv
import org.junit.jupiter.api.Test

@KtorExperimentalLocationsAPI
@KtorExperimentalAPI
internal class PersonRoutesKtTest {
    private val sakRepo = DatabaseBuilder.build(EmbeddedDatabase.instance())

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

        withTestApplication({
            testEnv()
            testSusebakover(httpClients = buildHttpClients(personOppslag = personoppslag(200, testIdent, testIdent)))
        }) {
            defaultRequest(Get, "$personPath/$testIdent")
        }.apply {
            assertEquals(OK, response.status())
            assertEquals(testIdent, response.content!!)
        }
    }

    @Test
    fun `skal propagere httpStatus fra PDL kall`() {
        val testIdent = "12345678910"
        val errorMessage = "beklager, det gikk dårlig"

        withTestApplication({
            testEnv()
            testSusebakover(httpClients = buildHttpClients(personOppslag = personoppslag(Unauthorized.value, errorMessage, testIdent)))
        }) {
            defaultRequest(Get, "$personPath/$testIdent")
        }.apply {
            assertEquals(Unauthorized, response.status())
            assertEquals(errorMessage, response.content!!)
        }
    }

    fun personoppslag(statusCode: Int, responseBody: String, testIdent: String) = object :
        PersonOppslag {
        override fun person(ident: Fnr): ClientResponse = when (testIdent) {
            ident.toString() -> ClientResponse(statusCode, responseBody)
            else -> ClientResponse(500, "funkitj")
        }

        override fun aktørId(ident: Fnr): String = throw NotImplementedError()
    }
}
