package no.nav.su.se.bakover

import com.github.tomakehurst.wiremock.client.WireMock
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
import io.ktor.util.Attributes
import io.ktor.util.KtorExperimentalAPI
import kotlinx.coroutines.io.ByteReadChannel
import kotlinx.coroutines.runBlocking
import no.nav.su.meldinger.kafka.soknad.SøknadInnholdTestdataBuilder.Companion.build
import no.nav.su.meldinger.kafka.soknad.SøknadInnholdTestdataBuilder.Companion.personopplysninger
import no.nav.su.se.bakover.ContextHolder.SecurityContext
import no.nav.su.se.bakover.soknad.søknadPath
import org.junit.jupiter.api.Test
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

@KtorExperimentalLocationsAPI
@KtorExperimentalAPI
internal class ContextHolderTest : ComponentTest() {

    @Test
    fun `should set and get context`() {
        ContextHolder.setSecurityContext(SecurityContext("token"))
        assertEquals("token", ContextHolder.getSecurityContext().token)
    }

    @Test
    fun `should preserve different contexts for different scopes`() {
        ContextHolder.setMdcContext(ContextHolder.MdcContext(mapOf(XCorrelationId to DEFAULT_CALL_ID)))
        runBlocking {
            ContextHolder.setSecurityContext(SecurityContext("outer"))
            val outer = Thread.currentThread()
            launchWithContext(callWithAuth("inner")) {
                val inner = Thread.currentThread()
                assertEquals("inner", ContextHolder.getSecurityContext().token)
                assertEquals(DEFAULT_CALL_ID, ContextHolder.getMdc(XCorrelationId))
                assertNotEquals(outer, inner)
                launchWithContext(callWithAuth("furtherin")) {
                    assertEquals("furtherin", ContextHolder.getSecurityContext().token)
                    assertEquals(DEFAULT_CALL_ID, ContextHolder.getMdc(XCorrelationId))
                }
            }
            assertEquals("outer", ContextHolder.getSecurityContext().token)
            assertEquals(DEFAULT_CALL_ID, ContextHolder.getMdc(XCorrelationId))
        }
    }

    @Test
    fun `parallel requests should preserve context`() {
        val token = jwtStub.createTokenFor()
        val numRequests = 100
        stubPdl()
        withTestApplication({
            testEnv(wireMockServer)
            susebakover()
        }) {
            val requests = List(numRequests) { CallableRequest(this, it, token) }
            val executors = Executors.newFixedThreadPool(numRequests)
            var applicationCalls: List<TestApplicationCall>? = null
            requests.map { executors.submit(it) }.also {
                applicationCalls = it.map { it.get() }
            }
            WireMock.verify(100, WireMock.getRequestedFor(WireMock.urlPathEqualTo("/person"))) // Expect 100 invocations to endpoint for getting aktørid
            val downstreamCorrelationIds = WireMock.getAllServeEvents()
                    .filter { it.request.url == "/person?ident=01010100001" }
                    .map { it.request.header(XCorrelationId).firstValue() }
            val passedCorrelationIds = List(numRequests) { it.toString() }
            assertEquals(100, passedCorrelationIds.size)
            assertEquals(100, downstreamCorrelationIds.size)
            assertTrue(downstreamCorrelationIds.containsAll(passedCorrelationIds)) // Verify all correlation ids passed along to service to get aktørid
            applicationCalls!!.forEach { assertEquals(it.request.header(XCorrelationId), it.response.headers[XCorrelationId]) } // Assert responses contain input correlation id
        }
    }

    fun stubPdl() = WireMock.stubFor(WireMock.get(WireMock.urlPathEqualTo("/person")).willReturn(WireMock.okJson("""{"aktoerId":"12345"}""".trimIndent())))

    internal class CallableRequest(
            val testApplicationEngine: TestApplicationEngine,
            val correlationId: Int,
            val token: String
    ) : Callable<TestApplicationCall> {
        override fun call(): TestApplicationCall {
            println("Test Thread: ${Thread.currentThread()}")
            return testApplicationEngine.handleRequest(Post, søknadPath) {
                addHeader(XCorrelationId, "$correlationId")
                addHeader(Authorization, "Bearer $token")
                addHeader(ContentType, Json.toString())
                setBody(build(personopplysninger = personopplysninger("01010100001")).toJson())
            }
        }
    }

    class callWithAuth(val token: String) : ApplicationCall {
        override val application: Application
            get() = throw NotImplementedError()
        override val attributes: Attributes
            get() = throw NotImplementedError()
        override val parameters: Parameters
            get() = throw NotImplementedError()
        override val request: ApplicationRequest
            get() = DummyRequest(token)
        override val response: ApplicationResponse
            get() = throw NotImplementedError()
    }

    class DummyRequest(val token: String) : ApplicationRequest {
        override val call: ApplicationCall
            get() = throw NotImplementedError()
        override val cookies: RequestCookies
            get() = throw NotImplementedError()
        override val headers: Headers
            get() = headersOf(Authorization, token)
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
