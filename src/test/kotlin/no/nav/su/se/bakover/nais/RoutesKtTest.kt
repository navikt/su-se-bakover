package no.nav.su.se.bakover.nais

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.withTestApplication
import io.ktor.util.KtorExperimentalAPI
import io.mockk.every
import io.mockk.mockk
import no.nav.su.se.bakover.JwtStub
import no.nav.su.se.bakover.inntekt.SuInntektClient
import no.nav.su.se.bakover.module
import no.nav.su.se.bakover.person.SuPersonClient
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

@KtorExperimentalAPI
internal class RoutesKtTest {

    @Test
    fun hello() {
        val suPersonClient = mockk<SuPersonClient>();
        every { suPersonClient.person() } returns "ALIVE"
        val suInntektClient = mockk<SuInntektClient>();
        every { suInntektClient.inntekt() } returns "A million dollars"

        withTestApplication(
                {
                    testEnv(wireMockServer)
                    module(suPersonClient, suInntektClient)
                }
        ) {
            handleRequest(HttpMethod.Get, "/hello")
        }.apply {
            assertEquals(HttpStatusCode.OK, response.status())
            assertEquals("http://localhost", response.headers.get(HttpHeaders.AccessControlAllowOrigin))
            assertTrue(response.content?.contains(", cause i.. i.. i... i")!!);
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
