package no.nav.su.se.bakover

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod.Companion.Get
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.withTestApplication
import io.ktor.util.KtorExperimentalAPI
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

@KtorExperimentalAPI
class CallIdTest {

    @Test
    fun `return 400 when missing callId on authenticated calls`() {
        withTestApplication({
            testEnv()
            usingMocks()
        }) {
            handleRequest(Get, secureEndpoint) {
                addHeader(HttpHeaders.Authorization, "Bearer ${jwtStub.createTokenFor()}")
            }
        }.apply {
            assertEquals(BadRequest, response.status())
        }
    }

    @Test
    fun `callId ok`() {
        withTestApplication({
            testEnv()
            usingMocks()
        }) {
            withCallId(Get, secureEndpoint) {
                addHeader(HttpHeaders.Authorization, "Bearer ${jwtStub.createTokenFor()}")
            }
        }.apply {
            assertEquals(OK, response.status())
        }
    }
}