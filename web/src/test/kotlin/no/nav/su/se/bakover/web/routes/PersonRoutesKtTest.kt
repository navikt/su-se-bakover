package no.nav.su.se.bakover.web.routes

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
import no.nav.su.se.bakover.database.DatabaseBuilder
import no.nav.su.se.bakover.database.EmbeddedDatabase
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.testEnv
import no.nav.su.se.bakover.web.ComponentTest
import no.nav.su.se.bakover.web.ON_BEHALF_OF_TOKEN
import no.nav.su.se.bakover.web.susebakover
import no.nav.su.se.bakover.withCorrelationId
import org.json.JSONObject
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

@KtorExperimentalLocationsAPI
@KtorExperimentalAPI
internal class PersonRoutesKtTest : ComponentTest() {

    private val sakRepo = DatabaseBuilder.fromDatasource(EmbeddedDatabase.database)

    @Test
    fun `får ikke hente persondata uten å være innlogget`() {
        withTestApplication({
            testEnv(wireMockServer)
            susebakover()
        }) {
            withCorrelationId(Get, "$personPath/12345678910")
        }.apply {
            assertEquals(HttpStatusCode.Unauthorized, response.status())
        }
    }

    @Test
    fun `bad request ved ugyldig fnr`() {
        withTestApplication({
            testEnv(wireMockServer)
            susebakover()
        }) {
            withCorrelationId(Get, "$personPath/qwertyuiopå") {
                addHeader(Authorization, jwt)
            }
        }.apply {
            assertEquals(HttpStatusCode.BadRequest, response.status())
        }
    }

    @Test
    fun `henter sak for fnr`() {
        withTestApplication(({
            testEnv(wireMockServer)
            susebakover()
        })) {
            val fnr = "12121212121"
            sakRepo.opprettSak(Fnr(fnr))
            withCorrelationId(Get, "$personPath/$fnr/sak") {
                addHeader(Authorization, jwt)
            }.apply {
                assertEquals(OK, response.status())
                assertEquals(fnr, JSONObject(response.content).getString("fnr"))
            }
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
            withCorrelationId(Get, "$personPath/$testIdent") {
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
            withCorrelationId(Get, "$personPath/$testIdent") {
                addHeader(Authorization, "Bearer $token")
            }
        }.apply {
            assertEquals(Unauthorized, response.status())
            assertEquals(errorMessage, response.content!!)
        }
    }
}
