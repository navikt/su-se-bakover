package no.nav.su.se.bakover.web.routes

import io.ktor.http.HttpMethod.Companion.Get
import io.ktor.http.HttpStatusCode.Companion.InternalServerError
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.http.HttpStatusCode.Companion.Unauthorized
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.withTestApplication
import no.nav.su.se.bakover.client.ClientResponse
import no.nav.su.se.bakover.client.inntekt.InntektOppslag
import no.nav.su.se.bakover.domain.Brukerrolle
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.web.TestClientsBuilder.testClients
import no.nav.su.se.bakover.web.defaultRequest
import no.nav.su.se.bakover.web.testSusebakover
import org.json.JSONObject
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

internal class InntektRoutesKtTest {

    private val ident = "12345678910"
    private val fraOgMedDato = "2020-01-01"
    private val tilOgMedDato = "2020-01-30"
    private val path = "/inntekt?ident=$ident&fraOgMedDato=$fraOgMedDato&tilOgMedDato=$tilOgMedDato"

    @Test
    fun `får ikke hente inntekt uten å være innlogget`() {
        withTestApplication({
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
            testSusebakover()
        }) {
            defaultRequest(Get, path, listOf(Brukerrolle.Saksbehandler))
        }.apply {
            assertEquals(OK, response.status())
            assertEquals(ident, JSONObject(response.content!!).getJSONObject("ident").getString("identifikator"))
        }
    }

    @Test
    fun `håndterer og videreformidler feil`() {
        val errorMessage =
            """{"message": "nich gut"}"""
        withTestApplication({
            testSusebakover(
                clients = testClients.copy(
                    inntektOppslag = object :
                        InntektOppslag {
                        override fun inntekt(
                            ident: Fnr,
                            innloggetSaksbehandlerToken: String,
                            fraOgMedDato: String,
                            tilOgMedDato: String
                        ) = ClientResponse(500, errorMessage)
                    }
                )
            )
        }) {
            defaultRequest(Get, path, listOf(Brukerrolle.Veileder))
        }.apply {
            assertEquals(InternalServerError, response.status())
            assertEquals(errorMessage, response.content!!)
        }
    }
}
