package no.nav.su.se.bakover.web.routes

import io.ktor.http.HttpMethod.Companion.Get
import io.ktor.http.HttpStatusCode.Companion.InternalServerError
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.http.HttpStatusCode.Companion.Unauthorized
import io.ktor.locations.KtorExperimentalLocationsAPI
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.withTestApplication
import io.ktor.util.KtorExperimentalAPI
import kotlin.test.assertEquals
import no.nav.su.se.bakover.client.ClientResponse
import no.nav.su.se.bakover.client.InntektOppslag
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.web.buildClients
import no.nav.su.se.bakover.web.defaultRequest
import no.nav.su.se.bakover.web.testEnv
import no.nav.su.se.bakover.web.testSusebakover
import org.json.JSONObject
import org.junit.jupiter.api.Test

@KtorExperimentalLocationsAPI
@KtorExperimentalAPI
internal class InntektRoutesKtTest {

    private val ident = "12345678910"
    private val fomDato = "2020-01-01"
    private val tomDato = "2020-01-30"
    private val path = "/inntekt?ident=$ident&fomDato=$fomDato&tomDato=$tomDato"

    @Test
    fun `får ikke hente inntekt uten å være innlogget`() {
        withTestApplication({
            testEnv()
            testSusebakover()
        }) {
            handleRequest(Get, path)
        }.apply {
            assertEquals(Unauthorized, response.status())
        }
    }

    @Test
    fun `kan hente inntekt`() {
        withTestApplication({
            testEnv()
            testSusebakover()
        }) {
            defaultRequest(Get, path)
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
            testSusebakover(httpClients = buildClients(inntektOppslag = object : InntektOppslag {
                override fun inntekt(ident: Fnr, innloggetSaksbehandlerToken: String, fomDato: String, tomDato: String) = ClientResponse(500, errorMessage)
            }))
        }) {
            defaultRequest(Get, path)
        }.apply {
            assertEquals(InternalServerError, response.status())
            assertEquals(errorMessage, response.content!!)
        }
    }
}
