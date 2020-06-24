package no.nav.su.se.bakover.web.routes

import io.ktor.http.HttpHeaders.Authorization
import io.ktor.http.HttpMethod.Companion.Get
import io.ktor.http.HttpStatusCode
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.http.HttpStatusCode.Companion.Unauthorized
import io.ktor.locations.KtorExperimentalLocationsAPI
import io.ktor.server.testing.withTestApplication
import io.ktor.util.KtorExperimentalAPI
import no.nav.su.se.bakover.client.ClientResponse
import no.nav.su.se.bakover.client.PersonOppslag
import no.nav.su.se.bakover.database.DatabaseBuilder
import no.nav.su.se.bakover.database.EmbeddedDatabase
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.testEnv
import no.nav.su.se.bakover.web.ComponentTest
import no.nav.su.se.bakover.web.susebakover
import no.nav.su.se.bakover.withCorrelationId
import org.json.JSONObject
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

@KtorExperimentalLocationsAPI
@KtorExperimentalAPI
internal class PersonRoutesKtTest : ComponentTest() {
    private val sakRepo = DatabaseBuilder.fromDatasource(EmbeddedDatabase.database)

    @Test
    fun `får ikke hente persondata uten å være innlogget`() {
        withTestApplication({
            testEnv(wireMockServer)
            susebakover(clients = buildClients(), jwkProvider = JwkProviderStub)
        }) {
            withCorrelationId(Get, "$personPath/12345678910")
        }.apply {
            assertEquals(HttpStatusCode.Unauthorized, response.status())
        }
    }

    @Test
    fun `bad request ved ugyldig fnr`() {
        withTestApplication({
            testEnv(wireMockServer)
            susebakover(clients = buildClients(), jwkProvider = JwkProviderStub)
        }) {
            withCorrelationId(Get, "$personPath/qwertyuiopå") {
                addHeader(Authorization, jwt)
            }
        }.apply {
            assertEquals(HttpStatusCode.BadRequest, response.status())
        }
    }

    @Test
    fun `henter sak for fnr`() {
        withTestApplication(({
            testEnv(wireMockServer)
            susebakover(clients = buildClients(), jwkProvider = JwkProviderStub)
        })) {
            val fnr = "12121212121"
            sakRepo.opprettSak(Fnr(fnr))
            withCorrelationId(Get, "$personPath/$fnr/sak") {
                addHeader(Authorization, jwt)
            }.apply {
                assertEquals(OK, response.status())
                assertEquals(fnr, JSONObject(response.content).getString("fnr"))
            }
        }
    }

    @Test
    fun `kan hente data gjennom PersonOppslag`() {
        val testIdent = "12345678910"

        withTestApplication({
            testEnv(wireMockServer)
            susebakover(clients = buildClients(personOppslag = personoppslag(200, testIdent, testIdent)), jwkProvider = JwkProviderStub)
        }) {
            withCorrelationId(Get, "$personPath/$testIdent") {
                addHeader(Authorization, jwt)
            }
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
            testEnv(wireMockServer)
            susebakover(clients = buildClients(personOppslag = personoppslag(Unauthorized.value, errorMessage, testIdent)), jwkProvider = JwkProviderStub)
        }) {
            withCorrelationId(Get, "$personPath/$testIdent") {
                addHeader(Authorization, jwt)
            }
        }.apply {
            assertEquals(Unauthorized, response.status())
            assertEquals(errorMessage, response.content!!)
        }
    }

    fun personoppslag(statusCode: Int, responseBody: String, testIdent: String) = object : PersonOppslag {
        override fun person(ident: Fnr): ClientResponse = when (testIdent) {
            ident.toString() -> ClientResponse(statusCode, responseBody)
            else -> ClientResponse(500, "funkitj")
        }

        override fun aktørId(ident: Fnr): String = TODO("Not yet implemented")
    }
}
