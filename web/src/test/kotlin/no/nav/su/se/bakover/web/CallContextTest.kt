package no.nav.su.se.bakover.web

import arrow.core.Either
import arrow.core.right
import io.ktor.http.ContentType.Application.Json
import io.ktor.http.HttpHeaders.Authorization
import io.ktor.http.HttpHeaders.ContentType
import io.ktor.http.HttpHeaders.XCorrelationId
import io.ktor.http.HttpMethod.Companion.Post
import io.ktor.request.header
import io.ktor.server.testing.TestApplicationCall
import io.ktor.server.testing.TestApplicationEngine
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.setBody
import io.ktor.server.testing.withTestApplication
import no.nav.su.se.bakover.client.ClientError
import no.nav.su.se.bakover.client.person.PersonOppslag
import no.nav.su.se.bakover.client.stubs.person.PersonOppslagStub
import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.domain.AktørId
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.Person
import no.nav.su.se.bakover.domain.SøknadInnhold
import no.nav.su.se.bakover.domain.SøknadInnholdTestdataBuilder
import no.nav.su.se.bakover.domain.SøknadInnholdTestdataBuilder.build
import no.nav.su.se.bakover.web.routes.søknad.SøknadInnholdJson.Companion.toSøknadInnholdJson
import no.nav.su.se.bakover.web.routes.søknad.søknadPath
import org.junit.jupiter.api.Test
import org.slf4j.MDC
import java.util.Collections.synchronizedList
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal class CallContextTest {

    @Test
    fun `parallel requests should preserve context`() {
        val numRequests = 100
        val downstreamCorrelationIds: MutableList<String> = synchronizedList(mutableListOf<String>())

        withTestApplication({
            testSusebakover(
                httpClients = buildHttpClients(
                    personOppslag = object :
                        PersonOppslag {
                        override fun person(fnr: Fnr): Either<ClientError, Person> = PersonOppslagStub.person(fnr)

                        override fun aktørId(fnr: Fnr) =
                            AktørId("aktørid".also { downstreamCorrelationIds.add(MDC.get("X-Correlation-ID")) }).right()
                    }
                )
            )
        }) {
            val requests = List(numRequests) { CallableRequest(this, it, Jwt.create()) }
            val executors = Executors.newFixedThreadPool(numRequests)
            val applicationCalls: List<TestApplicationCall>? = requests
                .map { executors.submit(it) }
                .map { it.get() }

            val passedCorrelationIds = List(numRequests) { it.toString() }
            assertEquals(numRequests, downstreamCorrelationIds.size, "downstreamCorrelationIds")
            assertTrue(downstreamCorrelationIds.containsAll(passedCorrelationIds)) // Verify all correlation ids passed along to service to get aktørid
            applicationCalls!!.forEach {
                assertEquals(
                    it.request.header(XCorrelationId),
                    it.response.headers[XCorrelationId]
                )
            }
        }
    }

    internal class CallableRequest(
        val testApplicationEngine: TestApplicationEngine,
        val correlationId: Int,
        val token: String
    ) : Callable<TestApplicationCall> {
        private val søknadInnhold: SøknadInnhold = build(personopplysninger = SøknadInnholdTestdataBuilder.personopplysninger(FnrGenerator.random().toString()))

        private val søknadInnholdJson: String = objectMapper.writeValueAsString(søknadInnhold.toSøknadInnholdJson())

        override fun call(): TestApplicationCall {
            println("Test Thread: ${Thread.currentThread()}")
            return testApplicationEngine.handleRequest(
                Post,
                søknadPath
            ) {
                addHeader(XCorrelationId, "$correlationId")
                addHeader(Authorization, token)
                addHeader(ContentType, Json.toString())
                setBody(søknadInnholdJson)
            }
        }
    }
}
