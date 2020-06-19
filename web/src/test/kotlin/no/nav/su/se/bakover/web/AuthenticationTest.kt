package no.nav.su.se.bakover.web

import io.ktor.http.HttpHeaders.Authorization
import io.ktor.http.HttpMethod.Companion.Get
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.http.HttpStatusCode.Companion.Unauthorized
import io.ktor.locations.KtorExperimentalLocationsAPI
import io.ktor.server.testing.withTestApplication
import io.ktor.util.KtorExperimentalAPI
import no.nav.su.se.bakover.jwtStub
import no.nav.su.se.bakover.testEnv
import no.nav.su.se.bakover.usingMocks
import no.nav.su.se.bakover.withCorrelationId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.*

const val secureEndpoint = "/authenticated"

@KtorExperimentalLocationsAPI
@KtorExperimentalAPI
internal class AuthenticationTest {

    @Test
    fun `secure endpoint krever autentisering`() {
        withTestApplication({
            testEnv()
            usingMocks()
        }) {
            withCorrelationId(Get, secureEndpoint)
        }.apply {
            assertEquals(Unauthorized, response.status())
        }
    }

    @Test
    fun `secure endpoint ok med gyldig token`() {
        val token = jwtStub.createTokenFor()

        withTestApplication({
            testEnv()
            usingMocks()
        }) {
            withCorrelationId(Get, secureEndpoint) {
                addHeader(Authorization, "Bearer $token")
            }
        }.apply {
            assertEquals(OK, response.status())
        }
    }

    @Test
    fun `forespørsel uten påkrevet audience skal svare med 401`() {
        val token = jwtStub.createTokenFor(audience = "wrong_audience")

        withTestApplication({
            testEnv()
            usingMocks()
        }) {
            withCorrelationId(Get, secureEndpoint) {
                addHeader(Authorization, "Bearer $token")
            }
        }.apply {
            assertEquals(Unauthorized, response.status())
        }
    }

    @Test
    fun `forespørsel uten medlemskap i påkrevet gruppe skal svare med 401`() {
        val token = jwtStub.createTokenFor(groups = listOf("WRONG_GROUP_UUID"))

        withTestApplication({
            testEnv()
            usingMocks()
        }) {
            withCorrelationId(Get, secureEndpoint) {
                addHeader(Authorization, "Bearer $token")
            }
        }.apply {
            assertEquals(Unauthorized, response.status())
        }
    }

    @Test
    fun `forespørsel med utgått token skal svare med 401`() {
        val token = jwtStub.createTokenFor(expiresAt = Date.from(Instant.now().minusSeconds(1)))

        withTestApplication({
            testEnv()
            usingMocks()
        }) {
            withCorrelationId(Get, secureEndpoint) {
                addHeader(Authorization, "Bearer $token")
            }
        }.apply {
            assertEquals(Unauthorized, response.status())
        }
    }

    @Test
    fun `kan refreshe tokens`() {
        withTestApplication({
            testEnv()
            usingMocks()
        }) {
            withCorrelationId(Get, "auth/refresh") {
                addHeader("refresh_token", "my.refresh.token")
            }
        }.apply {
            assertTrue(response.headers.contains("access_token"))
            assertTrue(response.headers.contains("refresh_token"))
            assertEquals(OK, response.status())
        }
    }
}


