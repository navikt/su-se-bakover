package no.nav.su.se.bakover.web.routes

import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.matching.AnythingPattern
import io.ktor.http.HttpHeaders.Authorization
import io.ktor.http.HttpHeaders.XCorrelationId
import io.ktor.http.HttpMethod.Companion.Get
import io.ktor.http.HttpStatusCode.Companion.InternalServerError
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.http.HttpStatusCode.Companion.Unauthorized
import io.ktor.locations.KtorExperimentalLocationsAPI
import io.ktor.server.testing.withTestApplication
import io.ktor.util.KtorExperimentalAPI
import no.nav.su.se.bakover.componentTest
import no.nav.su.se.bakover.testEnv
import no.nav.su.se.bakover.web.ComponentTest
import no.nav.su.se.bakover.web.ON_BEHALF_OF_TOKEN
import no.nav.su.se.bakover.withCorrelationId
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

@KtorExperimentalLocationsAPI
@KtorExperimentalAPI
internal class InntektRoutesKtTest : ComponentTest() {

    private val ident = "12345678910"
    private val fomDato = "2020-01-01"
    private val tomDato = "2020-01-30"
    private val path = "/inntekt?ident=$ident&fomDato=$fomDato&tomDato=$tomDato"

    @Test
    fun `får ikke hente inntekt uten å være innlogget`() {
        withTestApplication({
            testEnv(wireMockServer)
            componentTest(wireMockServer)
        }) {
            withCorrelationId(Get, path)
        }.apply {
            assertEquals(Unauthorized, response.status())
        }
    }

    @Test
    fun `kan hente inntekt`() {
        stubFor(get(urlPathEqualTo("/person"))
                .withHeader(Authorization, equalTo("Bearer $ON_BEHALF_OF_TOKEN"))
                .withHeader(XCorrelationId, AnythingPattern())
                .withQueryParam("ident", equalTo(ident))
                .willReturn(
                        okJson("""{"ident"="$ident"}""")
                )
        )
        stubFor(
                post(urlPathEqualTo("/inntekt"))
                        .withRequestBody(matching("fnr=$ident&fom=2020-01&tom=2020-01"))
                        .withHeader(Authorization, equalTo("Bearer $ON_BEHALF_OF_TOKEN"))
                        .withHeader(XCorrelationId, AnythingPattern())
                        .willReturn(
                                okJson("""{"ident"="$ident"}""")
                        )
        )

        val token = jwtStub.createTokenFor()

        withTestApplication({
            testEnv(wireMockServer)
            componentTest(wireMockServer)
        }) {
            withCorrelationId(Get, path) {
                addHeader(Authorization, "Bearer $token")
            }
        }.apply {
            assertEquals(OK, response.status())
            assertEquals("""{"ident"="$ident"}""", response.content!!)
        }
    }

    @Test
    fun `håndterer og videreformidler feil`() {
        val errorMessage = """{"message": "nich gut"}"""
        stubFor(get(urlPathEqualTo("/person"))
                .withHeader(Authorization, equalTo("Bearer $ON_BEHALF_OF_TOKEN"))
                .withHeader(XCorrelationId, AnythingPattern())
                .withQueryParam("ident", equalTo(ident))
                .willReturn(
                        okJson("""{"ident"="$ident"}""")
                )
        )
        stubFor(
                post(urlPathEqualTo("/inntekt"))
                        .withRequestBody(matching("fnr=$ident&fom=2020-01&tom=2020-01"))
                        .withHeader(Authorization, equalTo("Bearer $ON_BEHALF_OF_TOKEN"))
                        .withHeader(XCorrelationId, AnythingPattern())
                        .willReturn(
                                aResponse().withBody(errorMessage).withStatus(500)
                        )
        )

        val token = jwtStub.createTokenFor()

        withTestApplication({
            testEnv(wireMockServer)
            componentTest(wireMockServer)
        }) {
            withCorrelationId(Get, path) {
                addHeader(Authorization, "Bearer $token")
            }
        }.apply {
            assertEquals(InternalServerError, response.status())
            assertEquals(errorMessage, response.content!!)
        }
    }
}
