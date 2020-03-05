package no.nav.su.se.bakover.sak

import io.ktor.http.HttpHeaders.Authorization
import io.ktor.http.HttpMethod.Companion.Get
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.http.HttpStatusCode.Companion.NotFound
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.locations.KtorExperimentalLocationsAPI
import io.ktor.server.testing.TestApplicationEngine
import io.ktor.server.testing.TestApplicationResponse
import io.ktor.server.testing.withTestApplication
import io.ktor.util.KtorExperimentalAPI
import no.nav.su.se.bakover.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@KtorExperimentalAPI
@KtorExperimentalLocationsAPI
internal class SakComponentTest: ComponentTest() {

    private val sakFnr01 = "12345678911"
    private val sakFnr02 = "12345678912"
    private val sakFnr03 = "12345678913"

    @Test
    fun `oppretter og henter sak med id og fnr`() {
        withTestApplication(({
            testEnv(wireMockServer)
            susebakover()
        })) {
            val opprettSakResponse = opprettSak(sakFnr01)
            assertEquals(OK, opprettSakResponse.status())

            val opprettetId = JSONObject(opprettSakResponse.content).getLong("id")

            withCorrelationId(Get, "$sakPath/$opprettetId") {
                addHeader(Authorization, jwt)
            }.apply {
                assertEquals(OK, response.status())
                assertEquals(sakFnr01, JSONObject(response.content).getString("fnr"))
            }
        }
    }

    @Test
    fun `oppretter og henter sak med fnr`() {
        withTestApplication(({
            testEnv(wireMockServer)
            susebakover()
        })) {
            opprettSak(sakFnr01)

            withCorrelationId(Get, "$sakPath?ident=$sakFnr01") {
                addHeader(Authorization, jwt)
            }.apply {
                assertEquals(OK, response.status())
                assertEquals(sakFnr01, JSONObject(response.content).getString("fnr"))
            }
        }
    }

    @Test
    fun `henter alle saker`() {
        withTestApplication(({
            testEnv(wireMockServer)
            susebakover()
        })) {
            val first = JSONObject(opprettSak(sakFnr01).content)
            val second = JSONObject(opprettSak(sakFnr02).content)
            val third = JSONObject(opprettSak(sakFnr03).content)

            withCorrelationId(Get, "$sakPath") {
                addHeader(Authorization, jwt)
            }.apply {
                assertEquals(OK, response.status())
                val saker = JSONArray(response.content)
                assertHarSakMedId(saker, first.getInt("id"))
                assertHarSakMedId(saker, second.getInt("id"))
                assertHarSakMedId(saker, third.getInt("id"))
            }
        }
    }

    private fun assertHarSakMedId(saker: JSONArray, expectedId: Int) = assertTrue(saker.filter { it is JSONObject && it.getInt("id") == expectedId }.isNotEmpty())

    @Test
    fun `error handling`() {
        withTestApplication(({
            testEnv(wireMockServer)
            susebakover()
        })) {
            val opprettSakResponse = opprettSak(sakFnr01)
            assertEquals(OK, opprettSakResponse.status())

            /*
            FIXME: vi bør legge på validering av fnr. for nå vil vi lagre en ny sak med fnr 999 etter et slikt kall som dette
            withCorrelationId(Get, "$sakPath?ident=999") {
                addHeader(Authorization, jwt)
            }.apply {
                assertEquals(NotFound, response.status())
            }*/

            withCorrelationId(Get, "$sakPath/999") {
                addHeader(Authorization, jwt)
            }.apply {
                assertEquals(NotFound, response.status())
            }

            withCorrelationId(Get, "$sakPath/abcd") {
                addHeader(Authorization, jwt)
            }.apply {
                assertEquals(BadRequest, response.status())
            }
        }
    }

    fun TestApplicationEngine.opprettSak(fnr: String): TestApplicationResponse {
        return withCorrelationId(Get, "$sakPath?ident=$fnr") {
            addHeader(Authorization, jwt)
        }.response
    }
}