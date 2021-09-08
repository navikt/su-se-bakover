package no.nav.su.se.bakover.web

import arrow.core.Either
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.ConsoleAppender
import ch.qos.logback.core.read.ListAppender
import io.kotest.matchers.maps.shouldContainKey
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpHeaders.XCorrelationId
import io.ktor.http.HttpMethod.Companion.Get
import io.ktor.http.HttpMethod.Companion.Post
import io.ktor.http.HttpStatusCode.Companion.InternalServerError
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.server.testing.contentType
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.setBody
import io.ktor.server.testing.withTestApplication
import no.nav.su.se.bakover.domain.AktørId
import no.nav.su.se.bakover.domain.Brukerrolle
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.Person
import no.nav.su.se.bakover.domain.person.KunneIkkeHentePerson
import no.nav.su.se.bakover.domain.person.PersonOppslag
import no.nav.su.se.bakover.test.generer
import no.nav.su.se.bakover.web.TestClientsBuilder.testClients
import no.nav.su.se.bakover.web.routes.person.personPath
import no.nav.su.se.bakover.web.stubs.asBearerToken
import org.junit.jupiter.api.Test
import org.skyscreamer.jsonassert.JSONAssert
import org.slf4j.LoggerFactory

class RoutesTest {

    @Test
    fun `should add provided X-Correlation-ID header to response`() {
        withTestApplication({
            testSusebakover()
        }) {
            defaultRequest(Get, secureEndpoint, listOf(Brukerrolle.Veileder))
        }.apply {
            response.status() shouldBe OK
            response.headers[XCorrelationId] shouldBe DEFAULT_CALL_ID
        }
    }

    @Test
    fun `should generate X-Correlation-ID header if not present`() {
        withTestApplication({
            testSusebakover()
        }) {
            handleRequest(Get, secureEndpoint) {
                addHeader(HttpHeaders.Authorization, jwtStub.createJwtToken(roller = listOf(Brukerrolle.Veileder)).asBearerToken())
            }
        }.apply {
            response.status() shouldBe OK
            response.headers[XCorrelationId] shouldNotBe null
            response.headers[XCorrelationId] shouldNotBe DEFAULT_CALL_ID
        }
    }

    @Test
    fun `logs appropriate MDC values`() {
        val rootAppender =
            ((LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME) as Logger).getAppender("STDOUT_JSON")) as ConsoleAppender
        val appender = ListAppender<ILoggingEvent>().apply { start() }
        lateinit var applog: Logger
        withTestApplication(
            {
                testSusebakover()
                applog = environment.log as Logger
            },
        ) {
            applog.apply { addAppender(appender) }
            handleRequest(Get, secureEndpoint) {
                addHeader(
                    HttpHeaders.Authorization,
                    jwtStub.createJwtToken(roller = listOf(Brukerrolle.Veileder)).asBearerToken(),
                )
            }
        }
        val logStatement = appender.list.first { it.message.contains("200 OK") }
        val logbackFormatted = String(rootAppender.encoder.encode(logStatement))
        logStatement.mdcPropertyMap shouldContainKey "X-Correlation-ID"
        logStatement.mdcPropertyMap shouldContainKey "Authorization"
        logbackFormatted shouldContain "X-Correlation-ID"
        logbackFormatted shouldNotContain "Authorization"
    }

    @Test
    fun `should transform exceptions to appropriate error responses`() {
        withTestApplication({
            testSusebakover(
                clients = testClients.copy(
                    personOppslag = object :
                        PersonOppslag {
                        override fun person(fnr: Fnr): Either<KunneIkkeHentePerson, Person> =
                            throw RuntimeException("thrown exception")

                        override fun personMedSystembruker(fnr: Fnr): Either<KunneIkkeHentePerson, Person> =
                            throw RuntimeException("thrown exception")

                        override fun aktørId(fnr: Fnr) = throw RuntimeException("thrown exception")
                        override fun aktørIdMedSystembruker(fnr: Fnr): Either<KunneIkkeHentePerson, AktørId> =
                            throw RuntimeException("thrown exception")

                        override fun sjekkTilgangTilPerson(fnr: Fnr): Either<KunneIkkeHentePerson, Unit> = throw RuntimeException("thrown exception")
                    }
                )
            )
        }) {
            defaultRequest(Post, "$personPath/søk", listOf(Brukerrolle.Veileder)) {
                setBody("""{"fnr":"${Fnr.generer()}"}""")
            }
        }.apply {
            response.status() shouldBe InternalServerError
            JSONAssert.assertEquals("""{"message":"Ukjent feil"}""", response.content, true)
        }
    }

    @Test
    fun `should use content-type application-json by default`() {
        withTestApplication({
            testSusebakover()
        }) {
            defaultRequest(Post, "$personPath/søk", listOf(Brukerrolle.Veileder)) {
                setBody("""{"fnr":"${Fnr.generer()}"}""")
            }
        }.apply {
            response.contentType().toString() shouldBe "${ContentType.Application.Json}; charset=${Charsets.UTF_8}"
        }
    }
}
