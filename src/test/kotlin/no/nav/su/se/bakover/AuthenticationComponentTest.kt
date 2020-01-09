package no.nav.su.se.bakover

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import io.ktor.http.HttpHeaders.Authorization
import io.ktor.http.HttpMethod.Companion.Get
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.http.HttpStatusCode.Companion.Unauthorized
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.withTestApplication
import io.ktor.util.KtorExperimentalAPI
import no.nav.su.se.bakover.nais.testEnv
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.*
import kotlin.test.assertEquals

const val secureEndpoint = "/authenticated"

@KtorExperimentalAPI
internal class AuthenticationComponentTest {

    @Test
    fun `secure endpoint krever autentisering`() {
        withTestApplication({
            testEnv(wireMockServer)
            susebakover()
        }) {
            handleRequest(Get, secureEndpoint)
        }.apply {
            assertEquals(Unauthorized, response.status())
        }
    }

    @Test
    fun `secure endpoint ok med gyldig token`() {
        val token = jwtStub.createTokenFor()

        withTestApplication({
            testEnv(wireMockServer)
            susebakover()
        }) {
            handleRequest(Get, secureEndpoint) {
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
            testEnv(wireMockServer)
            susebakover()
        }) {
            handleRequest(Get, secureEndpoint) {
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
            testEnv(wireMockServer)
            susebakover()
        }) {
            handleRequest(Get, secureEndpoint) {
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
            testEnv(wireMockServer)
            susebakover()
        }) {
            handleRequest(Get, secureEndpoint) {
                addHeader(Authorization, "Bearer $token")
            }
        }.apply {
            Assertions.assertEquals(Unauthorized, response.status())
        }
    }

    companion object {
        private val wireMockServer: WireMockServer = WireMockServer(WireMockConfiguration.options().dynamicPort())
        private val jwtStub by lazy {
            JwtStub(wireMockServer)
        }

        @BeforeAll
        @JvmStatic
        fun start() {
            wireMockServer.start()
            WireMock.stubFor(jwtStub.stubbedJwkProvider())
            WireMock.stubFor(jwtStub.stubbedConfigProvider())
        }

        @AfterAll
        @JvmStatic
        fun stop() {
            wireMockServer.stop()
        }
    }
}


