package no.nav.su.se.bakover.web

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.ConsoleAppender
import ch.qos.logback.core.read.ListAppender
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
import no.nav.su.se.bakover.client.person.PersonOppslag
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.web.routes.personPath
import org.json.JSONObject
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

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
    fun `logs appropriate MDC values`() {
        val rootAppender = ((LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME) as Logger).getAppender("STDOUT_JSON")) as ConsoleAppender
        val appender = ListAppender<ILoggingEvent>().apply { start() }
        lateinit var applog: Logger
        withTestApplication({
            testEnv()
            testSusebakover()
            applog = environment.log as Logger
        }) {
            applog.apply { addAppender(appender) }
            handleRequest(Get, secureEndpoint) {
                addHeader(HttpHeaders.Authorization, Jwt.create())
            }
        }
        val logStatement = appender.list.first { it.message.contains("200 OK") }
        val logbackFormatted = String(rootAppender.encoder.encode(logStatement))
        assertTrue(logStatement.mdcPropertyMap.containsKey("X-Correlation-ID"))
        assertTrue(logStatement.mdcPropertyMap.containsKey("Authorization"))
        assertTrue(logbackFormatted.contains("X-Correlation-ID"))
        assertFalse(logbackFormatted.contains("Authorization"))
    }

    @Test
    fun `should transform exceptions to appropriate error responses`() {
        withTestApplication({
            testEnv()
            testSusebakover(httpClients = buildClients(personOppslag = object :
                PersonOppslag {
                override fun person(ident: Fnr) = throw RuntimeException("thrown exception")
                override fun aktørId(ident: Fnr) = throw RuntimeException("thrown exception")
            }))
        }) {
            defaultRequest(Get, "$personPath/${FnrGenerator.random()}")
        }.apply {
            assertEquals(InternalServerError, response.status())
            assertEquals("thrown exception", JSONObject(response.content).getString("message"))
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
            assertEquals(
                "${ContentType.Application.Json}; charset=${Charsets.UTF_8}",
                response.contentType().toString()
            )
        }
    }
}
