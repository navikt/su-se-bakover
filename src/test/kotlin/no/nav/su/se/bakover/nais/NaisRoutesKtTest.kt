package no.nav.su.se.bakover.nais

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import io.ktor.http.HttpMethod
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.withTestApplication
import io.ktor.util.KtorExperimentalAPI
import no.nav.su.se.bakover.JwtStub
import no.nav.su.se.bakover.susebakover
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

@KtorExperimentalAPI
internal class NaisRoutesKtTest {

    @Test
    fun naisRoutes() {
        withTestApplication({
            testEnv(wireMockServer)
            susebakover()
        }
        ) {
            handleRequest(HttpMethod.Get, "/isalive").apply {
                assertEquals(200, response.status()!!.value)
                assertEquals("ALIVE", response.content!!)
            }
            handleRequest(HttpMethod.Get, "/isready").apply {
                assertEquals(200, response.status()!!.value)
                assertEquals("READY", response.content!!)
            }
            handleRequest(HttpMethod.Get, "/metrics").apply {
                assertEquals(200, response.status()!!.value)
            }
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