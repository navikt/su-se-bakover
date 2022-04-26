package no.nav.su.se.bakover.web

import arrow.core.Either
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.ktor.client.request.header
import io.ktor.client.request.setBody
import io.ktor.client.utils.EmptyContent.headers
import io.ktor.client.utils.EmptyContent.status
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpHeaders.XCorrelationId
import io.ktor.http.HttpMethod.Companion.Get
import io.ktor.http.HttpMethod.Companion.Post
import io.ktor.http.HttpStatusCode.Companion.InternalServerError
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.JsonNull.content
import no.nav.su.se.bakover.domain.AktørId
import no.nav.su.se.bakover.domain.Brukerrolle
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.Person
import no.nav.su.se.bakover.domain.person.KunneIkkeHentePerson
import no.nav.su.se.bakover.domain.person.PersonOppslag
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.generer
import no.nav.su.se.bakover.web.routes.person.personPath
import no.nav.su.se.bakover.web.stubs.asBearerToken
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.skyscreamer.jsonassert.JSONAssert

// LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME) er ikke thread safe
@Execution(value = ExecutionMode.SAME_THREAD)
class RoutesTest {

    @Test
    fun `should add provided X-Correlation-ID header to response`() {
        testApplication {
            application {
                susebakover()
            }
            defaultRequest(Get, secureEndpoint, listOf(Brukerrolle.Veileder))
        }.apply {
            status shouldBe OK
            headers[XCorrelationId] shouldBe DEFAULT_CALL_ID
        }
    }

    @Test
    fun `should generate X-Correlation-ID header if not present`() {
        testApplication {
            application {
                testSusebakover()
            }
            defaultRequest(Get, secureEndpoint) {
                header(
                    HttpHeaders.Authorization,
                    jwtStub.createJwtToken(roller = listOf(Brukerrolle.Veileder)).asBearerToken(),
                )
            }
        }.apply {
            status shouldBe OK
            headers[XCorrelationId] shouldNotBe null
            headers[XCorrelationId] shouldNotBe DEFAULT_CALL_ID
        }
    }

    @Test
    fun `should transform exceptions to appropriate error responses`() {
        testApplication {
            application {
                testSusebakover(
                    clients = TestClientsBuilder(fixedClock, mock { on { utbetaling } doReturn mock() }).build(
                        applicationConfig,
                    ).copy(
                        personOppslag = object :
                            PersonOppslag {
                            override fun person(fnr: Fnr): Either<KunneIkkeHentePerson, Person> =
                                throw RuntimeException("thrown exception")

                            override fun personMedSystembruker(fnr: Fnr): Either<KunneIkkeHentePerson, Person> =
                                throw RuntimeException("thrown exception")

                            override fun aktørId(fnr: Fnr) = throw RuntimeException("thrown exception")
                            override fun aktørIdMedSystembruker(fnr: Fnr): Either<KunneIkkeHentePerson, AktørId> =
                                throw RuntimeException("thrown exception")

                            override fun sjekkTilgangTilPerson(fnr: Fnr): Either<KunneIkkeHentePerson, Unit> =
                                throw RuntimeException("thrown exception")
                        },
                    ),
                )
            }
            defaultRequest(Post, "$personPath/søk", listOf(Brukerrolle.Veileder)) {
                setBody("""{"fnr":"${Fnr.generer()}"}""")
            }
        }.apply {
            status shouldBe InternalServerError
            JSONAssert.assertEquals("""{"message":"Ukjent feil"}""", content, true)
        }
    }

    @Test
    fun `should use content-type application-json by default`() {
        testApplication {
            application {
                testSusebakover()
            }
            val response = defaultRequest(Post, "$personPath/søk", listOf(Brukerrolle.Veileder)) {
                setBody("""{"fnr":"${Fnr.generer()}"}""")
            }
            response.contentType().toString() shouldBe "${ContentType.Application.Json}; charset=${Charsets.UTF_8}"
        }
    }
}
