package no.nav.su.se.bakover.web

import io.ktor.application.Application
import io.ktor.application.ApplicationCall
import io.ktor.http.ContentType.Application.Json
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders.Authorization
import io.ktor.http.HttpHeaders.ContentType
import io.ktor.http.HttpHeaders.XCorrelationId
import io.ktor.http.HttpMethod.Companion.Post
import io.ktor.http.Parameters
import io.ktor.http.RequestConnectionPoint
import io.ktor.http.headersOf
import io.ktor.locations.KtorExperimentalLocationsAPI
import io.ktor.request.ApplicationReceivePipeline
import io.ktor.request.ApplicationRequest
import io.ktor.request.RequestCookies
import io.ktor.request.header
import io.ktor.response.ApplicationResponse
import io.ktor.server.testing.*
import io.ktor.util.AttributeKey
import io.ktor.util.Attributes
import io.ktor.util.KtorExperimentalAPI
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.runBlocking
import no.nav.su.meldinger.kafka.soknad.SøknadInnholdTestdataBuilder.Companion.build
import no.nav.su.meldinger.kafka.soknad.SøknadInnholdTestdataBuilder.Companion.personopplysninger
import no.nav.su.se.bakover.DEFAULT_CALL_ID
import no.nav.su.se.bakover.client.ClientResponse
import no.nav.su.se.bakover.client.PersonOppslag
import no.nav.su.se.bakover.common.CallContext
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.testEnv
import no.nav.su.se.bakover.web.routes.søknadPath
import org.junit.jupiter.api.Test
import java.util.Collections.synchronizedList
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@KtorExperimentalLocationsAPI
@KtorExperimentalAPI
internal class CallContextTest : ComponentTest() {

    @Test
    fun `should set and get context`() {
        CallContext(CallContext.SecurityContext("token"), CallContext.MdcContext(mapOf(XCorrelationId to "value")))
        assertEquals("token", CallContext.authentication())
        assertEquals("value", CallContext.correlationId())
    }

    @Test
    fun `should preserve different contexts for different scopes`() {
        runBlocking {
            launchWithContext(callWithAuth("outer", DEFAULT_CALL_ID)) {
                launchWithContext(callWithAuth("inner", DEFAULT_CALL_ID)) {
                    assertEquals("inner", CallContext.authentication())
                    assertEquals(DEFAULT_CALL_ID, CallContext.correlationId())
                    launchWithContext(callWithAuth("furtherin", DEFAULT_CALL_ID)) {
                        assertEquals("furtherin", CallContext.authentication())
                        assertEquals(DEFAULT_CALL_ID, CallContext.correlationId())
                    }
                }
                assertEquals("outer", CallContext.authentication())
                assertEquals(DEFAULT_CALL_ID, CallContext.correlationId())
            }
        }
    }

    @Test
    fun `parallel requests should preserve context`() {
        val numRequests = 100
        val downstreamCorrelationIds: MutableList<String> = synchronizedList(mutableListOf<String>())

        withTestApplication({
            testEnv(wireMockServer)
            susebakover(clients = buildClients(personOppslag = object : PersonOppslag {
                override fun person(ident: Fnr): ClientResponse = TODO("Not yet implemented")

                override fun aktørId(ident: Fnr): String =
                        "aktørid".also { downstreamCorrelationIds.add(CallContext.correlationId()) }
            }), jwkProvider = JwkProviderStub)
        }) {
            val requests = List(numRequests) { CallableRequest(this, it, jwt) }
            val executors = Executors.newFixedThreadPool(numRequests)
            var applicationCalls: List<TestApplicationCall>? = requests
                    .map { executors.submit(it) }
                    .map { it.get() }

            val passedCorrelationIds = List(numRequests) { it.toString() }
            assertEquals(numRequests, downstreamCorrelationIds.size, "downstreamCorrelationIds")
            assertTrue(downstreamCorrelationIds.containsAll(passedCorrelationIds)) // Verify all correlation ids passed along to service to get aktørid
            applicationCalls!!.forEach { assertEquals(it.request.header(XCorrelationId), it.response.headers[XCorrelationId]) } // Assert responses contain input correlation id
        }
    }

    internal class CallableRequest(
            val testApplicationEngine: TestApplicationEngine,
            val correlationId: Int,
            val token: String
    ) : Callable<TestApplicationCall> {
        override fun call(): TestApplicationCall {
            println("Test Thread: ${Thread.currentThread()}")
            return testApplicationEngine.handleRequest(Post, søknadPath) {
                addHeader(XCorrelationId, "$correlationId")
                addHeader(Authorization, token)
                addHeader(ContentType, Json.toString())
                setBody(build(personopplysninger = personopplysninger(FnrGenerator.random().toString())).toJson())
            }
        }
    }

    class callWithAuth(val token: String, val correlationId: String) : ApplicationCall {
        override val application: Application
            get() = throw NotImplementedError()
        override val attributes: Attributes
            get() = MyAttributes
        override val parameters: Parameters
            get() = throw NotImplementedError()
        override val request: ApplicationRequest
            get() = DummyRequest(token)
        override val response: ApplicationResponse
            get() = throw NotImplementedError()
    }

    object MyAttributes : Attributes {
        override val allKeys: List<AttributeKey<*>>
            get() = throw NotImplementedError()

        override fun <T : Any> computeIfAbsent(key: AttributeKey<T>, block: () -> T): T = throw NotImplementedError()
        override fun contains(key: AttributeKey<*>): Boolean = throw NotImplementedError()
        override fun <T : Any> getOrNull(key: AttributeKey<T>): T? = DEFAULT_CALL_ID as T
        override fun <T : Any> put(key: AttributeKey<T>, value: T): Unit = throw NotImplementedError()
        override fun <T : Any> remove(key: AttributeKey<T>): Unit = throw NotImplementedError()
    }

    class DummyRequest(val token: String) : ApplicationRequest {
        override val call: ApplicationCall
            get() = throw NotImplementedError()
        override val cookies: RequestCookies
            get() = throw NotImplementedError()
        override val headers: Headers
            get() = headersOf(Pair(Authorization, listOf(token)), Pair(XCorrelationId, listOf(DEFAULT_CALL_ID)))
        override val local: RequestConnectionPoint
            get() = throw NotImplementedError()
        override val pipeline: ApplicationReceivePipeline
            get() = throw NotImplementedError()
        override val queryParameters: Parameters
            get() = throw NotImplementedError()

        override fun receiveChannel(): ByteReadChannel {
            throw NotImplementedError()
        }
    }
}
