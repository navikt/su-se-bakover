package no.nav.su.se.bakover.web

import arrow.core.Either
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
import io.ktor.server.testing.contentType
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.withTestApplication
import no.nav.su.se.bakover.domain.Brukerrolle
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.Person
import no.nav.su.se.bakover.domain.person.KunneIkkeHentePerson
import no.nav.su.se.bakover.domain.person.PersonOppslag
import no.nav.su.se.bakover.web.TestClientsBuilder.testClients
import no.nav.su.se.bakover.web.routes.person.personPath
import org.junit.jupiter.api.Test
import org.skyscreamer.jsonassert.JSONAssert
import org.slf4j.LoggerFactory
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RoutesTest {

    @Test
    fun `should add provided X-Correlation-ID header to response`() {
        withTestApplication({
            testSusebakover()
        }) {
            defaultRequest(Get, secureEndpoint, listOf(Brukerrolle.Veileder))
        }.apply {
            assertEquals(OK, response.status())
            assertEquals(DEFAULT_CALL_ID, response.headers[XCorrelationId])
        }
    }

    @Test
    fun `should generate X-Correlation-ID header if not present`() {
        withTestApplication({
            testSusebakover()
        }) {
            handleRequest(Get, secureEndpoint) {
                addHeader(HttpHeaders.Authorization, Jwt.create(roller = listOf(Brukerrolle.Veileder)))
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
            testSusebakover()
            applog = environment.log as Logger
        }) {
            applog.apply { addAppender(appender) }
            handleRequest(Get, secureEndpoint) {
                addHeader(HttpHeaders.Authorization, Jwt.create(roller = listOf(Brukerrolle.Veileder)))
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
            testSusebakover(
                clients = testClients.copy(
                    personOppslag = object :
                        PersonOppslag {
                        override fun person(fnr: Fnr): Either<KunneIkkeHentePerson, Person> = throw RuntimeException("thrown exception")
                        override fun aktørId(fnr: Fnr) = throw RuntimeException("thrown exception")
                    }
                )
            )
        }) {
            defaultRequest(Get, "$personPath/${FnrGenerator.random()}", listOf(Brukerrolle.Veileder))
        }.apply {
            assertEquals(InternalServerError, response.status())
            JSONAssert.assertEquals("""{"message":"Ukjent feil"}""", response.content, true)
        }
    }

    @Test
    fun `should use content-type application-json by default`() {
        withTestApplication({
            testSusebakover()
        }) {
            defaultRequest(Get, "$personPath/${FnrGenerator.random()}", listOf(Brukerrolle.Veileder))
        }.apply {
            assertEquals(
                "${ContentType.Application.Json}; charset=${Charsets.UTF_8}",
                response.contentType().toString()
            )
        }
    }
}
