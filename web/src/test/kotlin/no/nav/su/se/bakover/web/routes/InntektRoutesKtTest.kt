package no.nav.su.se.bakover.web.routes

import io.ktor.http.HttpHeaders.Authorization
import io.ktor.http.HttpMethod.Companion.Get
import io.ktor.http.HttpStatusCode.Companion.InternalServerError
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.http.HttpStatusCode.Companion.Unauthorized
import io.ktor.locations.KtorExperimentalLocationsAPI
import io.ktor.server.testing.withTestApplication
import io.ktor.util.KtorExperimentalAPI
import no.nav.su.se.bakover.client.ClientResponse
import no.nav.su.se.bakover.client.InntektOppslag
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.testEnv
import no.nav.su.se.bakover.web.ComponentTest
import no.nav.su.se.bakover.web.susebakover
import no.nav.su.se.bakover.withCorrelationId
import org.json.JSONObject
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
            testEnv()
            susebakover(clients = buildClients(), jwkProvider = JwkProviderStub)
        }) {
            withCorrelationId(Get, path)
        }.apply {
            assertEquals(Unauthorized, response.status())
        }
    }

    @Test
    fun `kan hente inntekt`() {
        withTestApplication({
            testEnv()
            susebakover(clients = buildClients(), jwkProvider = JwkProviderStub)
        }) {
            withCorrelationId(Get, path) {
                addHeader(Authorization, jwt)
            }
        }.apply {
            assertEquals(OK, response.status())
            assertEquals(ident, JSONObject(response.content!!).getJSONObject("ident").getString("identifikator"))
        }
    }

    @Test
    fun `håndterer og videreformidler feil`() {
        val errorMessage = """{"message": "nich gut"}"""
        withTestApplication({
            testEnv()
            susebakover(clients = buildClients(inntektOppslag = object : InntektOppslag {
                override fun inntekt(ident: Fnr, innloggetSaksbehandlerToken: String, fomDato: String, tomDato: String) = ClientResponse(500, errorMessage)
            }), jwkProvider = JwkProviderStub)
        }) {
            withCorrelationId(Get, path) {
                addHeader(Authorization, jwt)
            }
        }.apply {
            assertEquals(InternalServerError, response.status())
            assertEquals(errorMessage, response.content!!)
        }
    }
}
