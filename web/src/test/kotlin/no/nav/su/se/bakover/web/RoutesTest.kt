package no.nav.su.se.bakover.web

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpHeaders.XCorrelationId
import io.ktor.http.HttpMethod.Companion.Get
import io.ktor.http.HttpStatusCode.Companion.InternalServerError
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.locations.KtorExperimentalLocationsAPI
import io.ktor.server.testing.contentType
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.withTestApplication
import io.ktor.util.KtorExperimentalAPI
import no.nav.su.se.bakover.client.PersonOppslag
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.web.routes.personPath
import org.json.JSONObject
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull

@KtorExperimentalLocationsAPI
@KtorExperimentalAPI
class RoutesTest {

    @Test
    fun `should add provided X-Correlation-ID header to response`() {
        withTestApplication({
            testEnv()
            testSusebakover()
        }) {
            defaultRequest(Get, secureEndpoint)
        }.apply {
            assertEquals(OK, response.status())
            assertEquals(DEFAULT_CALL_ID, response.headers[XCorrelationId])
        }
    }

    @Test
    fun `should generate X-Correlation-ID header if not present`() {
        withTestApplication({
            testEnv()
            testSusebakover()
        }) {
            handleRequest(Get, secureEndpoint) {
                addHeader(HttpHeaders.Authorization, Jwt.create())
            }
        }.apply {
            assertEquals(OK, response.status())
            assertNotNull(response.headers[XCorrelationId])
            assertNotEquals(DEFAULT_CALL_ID, response.headers[XCorrelationId])
        }
    }

    @Test
    fun `should transform exceptions to appropriate error responses`() {
        withTestApplication({
            testEnv()
            testSusebakover(clients = buildClients(personOppslag = object : PersonOppslag {
                override fun person(ident: Fnr) = throw RuntimeException("thrown exception")
                override fun aktørId(ident: Fnr) = throw RuntimeException("thrown exception")
            }))
        }) {
            defaultRequest(Get, "$personPath/${FnrGenerator.random()}")
        }.apply {
            assertEquals(InternalServerError, response.status())
            assertEquals("thrown exception", JSONObject(response.content).getString("detailMessage"))
        }
    }

    @Test
    fun `should use content-type application-json by default`() {
        withTestApplication({
            testEnv()
            testSusebakover()
        }) {
            defaultRequest(Get, "$personPath/${FnrGenerator.random()}")
        }.apply {
            assertEquals("${ContentType.Application.Json}; charset=${Charsets.UTF_8}", response.contentType().toString())
        }
    }
}
