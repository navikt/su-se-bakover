package no.nav.su.se.bakover.web

import com.github.tomakehurst.wiremock.client.WireMock
import io.ktor.http.HttpHeaders.XCorrelationId
import io.ktor.http.HttpStatusCode
import io.ktor.http.HttpStatusCode.Companion.OK
import no.nav.su.se.bakover.DEFAULT_CALL_ID
import no.nav.su.se.bakover.client.ClientResponse
import no.nav.su.se.bakover.client.OAuth
import no.nav.su.se.bakover.client.PersonOppslag
import no.nav.su.se.bakover.common.CallContext
import no.nav.su.se.bakover.domain.Fnr
import org.json.JSONObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class InntektClientTest : ComponentTest() {

    @Test
    fun `skal ikke kalle inntekt om person gir feil`() {
        val inntektClient = SuInntektClient(wireMockServer.baseUrl(), clientId, tokenExchange, persontilgang403)
        val result = inntektClient.inntekt(Fnr("01010112345"), "innlogget bruker", "2000-01", "2000-12")
        assertEquals(ClientResponse(403, "Du hakke lov"), result)
    }

    @Test
    fun `skal kalle inntekt om person gir OK`() {
        val inntektClient = SuInntektClient(wireMockServer.baseUrl(), clientId, tokenExchange, persontilgang200)
        val result = inntektClient.inntekt(Fnr("01010112345"), "innlogget bruker", "2000-01", "2000-12")
        assertEquals(ClientResponse(200,"{}"), result)
    }

    private val clientId = "inntektclientid"
    private val persontilgang200 = object : PersonOppslag {
        override fun person(ident: Fnr): ClientResponse = ClientResponse(200, """{"ting": "OK"}""")
        override fun aktørId(ident: Fnr): String = "aktoerId"
    }
    private val persontilgang403 = object : PersonOppslag {
        override fun person(ident: Fnr): ClientResponse = ClientResponse(403, "Du hakke lov")
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