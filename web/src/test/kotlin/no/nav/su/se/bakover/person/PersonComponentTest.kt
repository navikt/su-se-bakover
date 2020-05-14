package no.nav.su.se.bakover.person

import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.matching.AnythingPattern
import io.ktor.http.HttpHeaders.Authorization
import io.ktor.http.HttpHeaders.XCorrelationId
import io.ktor.http.HttpMethod.Companion.Get
import io.ktor.http.HttpStatusCode
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.http.HttpStatusCode.Companion.Unauthorized
import io.ktor.locations.KtorExperimentalLocationsAPI
import io.ktor.server.testing.withTestApplication
import io.ktor.util.KtorExperimentalAPI
import no.nav.su.se.bakover.*
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

@KtorExperimentalLocationsAPI
@KtorExperimentalAPI
internal class PersonComponentTest: ComponentTest() {

    @Test
    fun `får ikke hente persondata uten å være innlogget`() {
        withTestApplication({
            testEnv(wireMockServer)
            susebakover()
        }) {
            withCorrelationId(Get, personPath)
        }.apply {
            assertEquals(HttpStatusCode.Unauthorized, response.status())
        }
    }

    @Test
    fun `kan hente persondata`() {
        val testIdent = "12345678910"
        stubFor(get(urlPathEqualTo("/person"))
                .withHeader(Authorization, equalTo("Bearer $ON_BEHALF_OF_TOKEN"))
                .withHeader(XCorrelationId, AnythingPattern())
                .withQueryParam("ident", equalTo(testIdent))
                .willReturn(
                        okJson("""{"ident"="$testIdent"}""")
                )
        )

        val token = jwtStub.createTokenFor()

        withTestApplication({
            testEnv(wireMockServer)
            susebakover()
        }) {
            withCorrelationId(Get, "$personPath?${Fødselsnummer.identLabel}=$testIdent") {
                addHeader(Authorization, "Bearer $token")
            }
        }.apply {
            assertEquals(OK, response.status())
            assertEquals("""{"ident"="$testIdent"}""", response.content!!)
        }
    }

    @Test
    fun `skal propagere httpStatus fra PDL kall`() {
        val testIdent = "12345678910"
        val errorMessage = "beklager, det gikk dårlig"
        stubFor(get(urlPathEqualTo("/person"))
                .withHeader(Authorization, equalTo("Bearer $ON_BEHALF_OF_TOKEN"))
                .withHeader(XCorrelationId, AnythingPattern())
                .withQueryParam("ident", equalTo(testIdent))
                .willReturn(aResponse().withBody(errorMessage).withStatus(401))
        )

        val token = jwtStub.createTokenFor()

        withTestApplication({
            testEnv(wireMockServer)
            susebakover()
        }) {
            withCorrelationId(Get, "$personPath?${Fødselsnummer.identLabel}=$testIdent") {
                addHeader(Authorization, "Bearer $token")
            }
        }.apply {
            assertEquals(Unauthorized, response.status())
            assertEquals("""{"message": "$errorMessage"}""", response.content!!)
        }
    }

}
