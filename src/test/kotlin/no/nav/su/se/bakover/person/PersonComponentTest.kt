package no.nav.su.se.bakover.person

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import io.ktor.http.HttpHeaders.Authorization
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.withTestApplication
import io.ktor.util.KtorExperimentalAPI
import no.nav.su.se.bakover.JwtStub
import no.nav.su.se.bakover.ON_BEHALF_OF_TOKEN
import no.nav.su.se.bakover.nais.SU_PERSON_PATH
import no.nav.su.se.bakover.nais.testEnv
import no.nav.su.se.bakover.personPath
import no.nav.su.se.bakover.susebakover
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

@KtorExperimentalAPI
internal class PersonComponentTest {

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
            WireMock.stubFor(jwtStub.stubbedTokenExchange())
        }

        @AfterAll
        @JvmStatic
        fun stop() {
            wireMockServer.stop()
        }
    }

    @Test
    fun `får ikke hente persondata uten å være innlogget`() {
        withTestApplication({
            testEnv(wireMockServer)
            susebakover()
        }) {
            handleRequest(HttpMethod.Get, personPath)
        }.apply {
            assertEquals(HttpStatusCode.Unauthorized, response.status())
        }
    }

    @Test
    fun `kan hente persondata`() {
        WireMock.stubFor(
            get(urlPathEqualTo("$SU_PERSON_PATH/isalive"))
                .withHeader(Authorization, equalTo("Bearer $ON_BEHALF_OF_TOKEN"))
                .willReturn(
                ok("123")
            )
        )

        val token = jwtStub.createTokenFor()

        withTestApplication({
            testEnv(wireMockServer)
            susebakover()
        }) {
            handleRequest(HttpMethod.Get, personPath) {
                addHeader(Authorization, "Bearer $token")
            }
        }.apply {
            assertEquals(HttpStatusCode.OK, response.status())
            assertEquals("123", response.content!!)
        }
    }

}