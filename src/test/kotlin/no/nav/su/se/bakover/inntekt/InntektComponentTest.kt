package no.nav.su.se.bakover.inntekt

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import io.ktor.http.HttpHeaders.Authorization
import io.ktor.http.HttpHeaders.XRequestId
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.withTestApplication
import io.ktor.util.KtorExperimentalAPI
import no.nav.su.se.bakover.JwtStub
import no.nav.su.se.bakover.ON_BEHALF_OF_TOKEN
import no.nav.su.se.bakover.nais.DEFAULT_CALL_ID
import no.nav.su.se.bakover.nais.SU_INNTEKT_PATH
import no.nav.su.se.bakover.nais.testEnv
import no.nav.su.se.bakover.nais.withDefaultHeaders
import no.nav.su.se.bakover.susebakover
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

@KtorExperimentalAPI
internal class InntektComponentTest {

    @Test
    fun `får ikke hente inntekt uten å være innlogget`() {
        withTestApplication({
            testEnv(wireMockServer)
            susebakover()
        }) {
            withDefaultHeaders(HttpMethod.Get, inntektPath)
        }.apply {
            assertEquals(HttpStatusCode.Unauthorized, response.status())
        }
    }

    @Test
    fun `kan hente inntekt`() {
        val testIdent = "12345678910"
        stubFor(get(urlPathEqualTo(SU_INNTEKT_PATH))
                .withHeader(Authorization, equalTo("Bearer $ON_BEHALF_OF_TOKEN"))
                .withHeader(XRequestId, equalTo(DEFAULT_CALL_ID))
                .withQueryParam(suInntektIdentLabel, equalTo(testIdent))
                .willReturn(
                        okJson("""{"ident"="$testIdent"}""")
                )
        )

        val token = jwtStub.createTokenFor()

        withTestApplication({
            testEnv(wireMockServer)
            susebakover()
        }) {
            withDefaultHeaders(HttpMethod.Get, "$inntektPath?$identLabel=$testIdent") {
                addHeader(Authorization, "Bearer $token")
            }
        }.apply {
            assertEquals(HttpStatusCode.OK, response.status())
            assertEquals("""{"ident"="$testIdent"}""", response.content!!)
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
            stubFor(jwtStub.stubbedJwkProvider())
            stubFor(jwtStub.stubbedConfigProvider())
            stubFor(jwtStub.stubbedTokenExchange())
        }

        @AfterAll
        @JvmStatic
        fun stop() {
            wireMockServer.stop()
        }
    }
}