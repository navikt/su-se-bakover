package no.nav.su.se.bakover

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod.Companion.Get
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.withTestApplication
import io.ktor.util.KtorExperimentalAPI
import no.nav.su.se.bakover.nais.testEnv
import no.nav.su.se.bakover.nais.withDefaultHeaders
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

@KtorExperimentalAPI
class CallIdTest {

    @Test
    fun `return 400 when missing callId on authenticated calls`() {
        withTestApplication({
            testEnv(wireMockServer)
            susebakover()
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
            testEnv(wireMockServer)
            susebakover()
        }) {
            withDefaultHeaders(Get, secureEndpoint) {
                addHeader(HttpHeaders.Authorization, "Bearer ${jwtStub.createTokenFor()}")
            }
        }.apply {
            assertEquals(OK, response.status())
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