package no.nav.su.se.bakover

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpHeaders.XCorrelationId
import io.ktor.http.HttpMethod.Companion.Get
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.locations.KtorExperimentalLocationsAPI
import io.ktor.server.testing.withTestApplication
import io.ktor.util.KtorExperimentalAPI
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@KtorExperimentalLocationsAPI
@KtorExperimentalAPI
class CallIdTest {

    @Test
    fun `should add X-Request-Id header to response`() {
        withTestApplication({
            testEnv()
            usingMocks()
        }) {
            withCorrelationId(Get, secureEndpoint) {
                addHeader(HttpHeaders.Authorization, "Bearer ${jwtStub.createTokenFor()}")
            }
        }.apply {
            assertEquals(OK, response.status())
            assertNotNull(response.headers[XCorrelationId])
        }
    }
}
