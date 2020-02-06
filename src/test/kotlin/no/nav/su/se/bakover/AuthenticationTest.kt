package no.nav.su.se.bakover

import io.ktor.http.HttpHeaders.Authorization
import io.ktor.http.HttpMethod.Companion.Get
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.http.HttpStatusCode.Companion.Unauthorized
import io.ktor.locations.KtorExperimentalLocationsAPI
import io.ktor.server.testing.withTestApplication
import io.ktor.util.KtorExperimentalAPI
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.*
import kotlin.test.assertEquals

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
            withCallId(Get, secureEndpoint)
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
            withCallId(Get, secureEndpoint) {
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
            withCallId(Get, secureEndpoint) {
                addHeader(Authorization, "Bearer $token")
            }
        }.apply {
            Assertions.assertEquals(Unauthorized, response.status())
        }
    }

    @Test
    fun `forespørsel uten medlemskap i påkrevet gruppe skal svare med 401`() {
        val token = jwtStub.createTokenFor(groups = listOf("WRONG_GROUP_UUID"))

        withTestApplication({
            testEnv()
            usingMocks()
        }) {
            withCallId(Get, secureEndpoint) {
                addHeader(Authorization, "Bearer $token")
            }
        }.apply {
            Assertions.assertEquals(Unauthorized, response.status())
        }
    }

    @Test
    fun `forespørsel med utgått token skal svare med 401`() {
        val token = jwtStub.createTokenFor(expiresAt = Date.from(Instant.now().minusSeconds(1)))

        withTestApplication({
            testEnv()
            usingMocks()
        }) {
            withCallId(Get, secureEndpoint) {
                addHeader(Authorization, "Bearer $token")
            }
        }.apply {
            Assertions.assertEquals(Unauthorized, response.status())
        }
    }
}


