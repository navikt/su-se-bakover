package no.nav.su.se.bakover.inntekt

import com.github.tomakehurst.wiremock.client.WireMock
import io.ktor.http.HttpHeaders.XCorrelationId
import io.ktor.http.HttpStatusCode
import io.ktor.http.HttpStatusCode.Companion.OK
import no.nav.su.se.bakover.*
import org.json.JSONObject
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

internal class InntektClientTest : ComponentTest() {

    @Test
    fun `skal ikke kalle inntekt om person gir feil`() {
        val inntektClient = SuInntektClient(wireMockServer.baseUrl(), clientId, tokenExchange, persontilgang403)
        val result = inntektClient.inntekt(Fnr("01010112345"), "innlogget bruker", "2000-01", "2000-12")
        assertEquals(Resultat.resultatMedMelding(HttpStatusCode.fromValue(403), "Du hakke lov"), result)
    }

    @Test
    fun `skal kalle inntekt om person gir OK`() {
        val inntektClient = SuInntektClient(wireMockServer.baseUrl(), clientId, tokenExchange, persontilgang200)
        val result = inntektClient.inntekt(Fnr("01010112345"), "innlogget bruker", "2000-01", "2000-12")
        assertEquals(OK.json("{}"), result)
    }

    private val clientId = "inntektclientid"
    private val persontilgang200 = object : PersonOppslag {
        override fun person(ident: Fnr): Resultat =
                OK.json("""{"ting": "OK"}""")

        override fun aktørId(ident: Fnr): String = "aktoerId"
    }
    private val persontilgang403 = object : PersonOppslag {
        override fun person(ident: Fnr): Resultat =
                HttpStatusCode.fromValue(403).tekst("Du hakke lov")

        override fun aktørId(ident: Fnr): String = "aktoerId"
    }
    private val tokenExchange = object : OAuth {
        override fun onBehalfOFToken(originalToken: String, otherAppId: String): String = "ON BEHALF OF!"
        override fun refreshTokens(refreshToken: String): JSONObject = JSONObject("""{"access_token":"abc","refresh_token":"cba"}""")
        override fun token(otherAppId: String): String = "token"
    }

    @BeforeEach
    fun setup() {
        CallContext(CallContext.SecurityContext("token"), CallContext.MdcContext(mapOf(XCorrelationId to DEFAULT_CALL_ID)))
        wireMockServer.stubFor(WireMock.post(WireMock.urlPathEqualTo("/inntekt"))
                .willReturn(WireMock.okJson("""{}""")))
    }
}