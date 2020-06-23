package no.nav.su.se.bakover.web

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpHeaders.XCorrelationId
import io.ktor.http.HttpMethod.Companion.Get
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.locations.KtorExperimentalLocationsAPI
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.withTestApplication
import io.ktor.util.KtorExperimentalAPI
import no.nav.su.se.bakover.DEFAULT_CALL_ID
import no.nav.su.se.bakover.testEnv
import no.nav.su.se.bakover.usingMocks
import no.nav.su.se.bakover.withCorrelationId
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull

@KtorExperimentalLocationsAPI
@KtorExperimentalAPI
class CallIdTest {

    @Test
    fun `should add provided X-Correlation-ID header to response`() {
        withTestApplication({
            testEnv()
            usingMocks()
        }) {
            withCorrelationId(Get, secureEndpoint) {
                addHeader(HttpHeaders.Authorization, Jwt.create())
            }
        }.apply {
            assertEquals(OK, response.status())
            assertEquals(DEFAULT_CALL_ID, response.headers[XCorrelationId])
        }
    }

    @Test
    fun `should generate X-Correlation-ID header if not present`() {
        withTestApplication({
            testEnv()
            usingMocks()
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
}
