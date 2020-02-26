package no.nav.su.se.bakover.sak

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
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
import org.junit.jupiter.api.*
import kotlin.test.assertEquals

@KtorExperimentalAPI
@KtorExperimentalLocationsAPI
internal class SakComponentTest {

    private val jwt = "Bearer ${jwtStub.createTokenFor()}"
    private val sakFnr01 = "12345678911"
    private val sakFnr02 = "12345678912"
    private val sakFnr03 = "12345678913"

    @AfterEach
    fun `reset database with great fury`() {
        EmbeddedDatabase.refresh()
    }

    @Test
    fun `oppretter og henter sak med id og fnr`() {
        withTestApplication(({
            testEnv(wireMockServer)
            susebakover()
        })) {
            val opprettSakResponse = opprettSak(sakFnr01)
            assertEquals(OK, opprettSakResponse.status())

            val opprettetId = JSONObject(opprettSakResponse.content).getLong("id")

            withCallId(Get, "$sakPath/$opprettetId") {
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

            withCallId(Get, "$sakPath?ident=$sakFnr01") {
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
            val first = opprettSak(sakFnr01).content
            val second = opprettSak(sakFnr02).content
            val third = opprettSak(sakFnr03).content

            withCallId(Get, "$sakPath") {
                addHeader(Authorization, jwt)
            }.apply {
                assertEquals(OK, response.status())
                val saker = JSONArray(response.content)
                assertEquals(3, saker.length())
                assertEquals(JSONObject(first).getInt("id"), saker.getJSONObject(0).getInt("id"))
                assertEquals(JSONObject(second).getInt("id"), saker.getJSONObject(1).getInt("id"))
                assertEquals(JSONObject(third).getInt("id"), saker.getJSONObject(2).getInt("id"))
            }
        }
    }

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
            withCallId(Get, "$sakPath?ident=999") {
                addHeader(Authorization, jwt)
            }.apply {
                assertEquals(NotFound, response.status())
            }*/

            withCallId(Get, "$sakPath/999") {
                addHeader(Authorization, jwt)
            }.apply {
                assertEquals(NotFound, response.status())
            }

            withCallId(Get, "$sakPath/abcd") {
                addHeader(Authorization, jwt)
            }.apply {
                assertEquals(BadRequest, response.status())
            }
        }
    }

    fun TestApplicationEngine.opprettSak(fnr: String): TestApplicationResponse {
        return withCallId(Get, "$sakPath?ident=$fnr") {
            addHeader(Authorization, jwt)
        }.response
    }

    companion object {
        private val wireMockServer: WireMockServer = WireMockServer(WireMockConfiguration.options().dynamicPort())
        private val jwtStub by lazy { JwtStub(wireMockServer) }

        @BeforeAll
        @JvmStatic
        fun start() {
            wireMockServer.start()
            WireMock.stubFor(jwtStub.stubbedJwkProvider())
            WireMock.stubFor(jwtStub.stubbedConfigProvider())
        }

        @AfterAll
        @JvmStatic
        fun stop() {
            wireMockServer.stop()
        }
    }
}