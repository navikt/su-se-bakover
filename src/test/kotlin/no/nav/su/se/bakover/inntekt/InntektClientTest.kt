package no.nav.su.se.bakover.inntekt

import com.github.kittinunf.fuel.core.*
import com.github.kittinunf.fuel.toolbox.HttpClient
import io.ktor.http.HttpHeaders.XCorrelationId
import io.ktor.http.HttpStatusCode
import io.ktor.http.HttpStatusCode.Companion.OK
import no.nav.su.se.bakover.Fødselsnummer
import no.nav.su.se.bakover.Resultat
import no.nav.su.se.bakover.azure.OAuth
import no.nav.su.se.bakover.json
import no.nav.su.se.bakover.person.PersonOppslag
import no.nav.su.se.bakover.tekst
import org.json.JSONObject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.MDC
import java.net.URL
import kotlin.test.assertEquals

internal class InntektClientTest {

    @Test
    fun `skal ikke kalle inntekt om person gir feil`() {
        val inntektClient = SuInntektClient(url, clientId, tokenExchange, persontilgang403)
        val result = inntektClient.inntekt(Fødselsnummer("01010112345"), "innlogget bruker", "2000-01", "2000-12")
        assertEquals(Resultat.resultatMedMelding(HttpStatusCode.fromValue(403), "Du hakke lov"), result)
    }

    @Test
    fun `skal kalle inntekt om person gir OK`() {
        val inntektClient = SuInntektClient(url, clientId, tokenExchange, persontilgang200)
        val result = inntektClient.inntekt(Fødselsnummer("01010112345"), "innlogget bruker", "2000-01", "2000-12")
        assertEquals(OK.json(""), result)
    }

    private val url = "http://some.place"
    private val clientId = "inntektclientid"
    private val persontilgang200 = object : PersonOppslag {
        override fun person(ident: Fødselsnummer, innloggetSaksbehandlerToken: String): Resultat =
                OK.json("""{"ting": "OK"}""")
        override fun aktørId(ident: Fødselsnummer, srvUserToken: String): String = "aktoerId"
    }
    private val persontilgang403 = object : PersonOppslag {
        override fun person(ident: Fødselsnummer, innloggetSaksbehandlerToken: String): Resultat =
                HttpStatusCode.fromValue(403).tekst("Du hakke lov")
        override fun aktørId(ident: Fødselsnummer, srvUserToken: String): String = "aktoerId"
    }
    private val tokenExchange = object : OAuth {
        override fun onBehalfOFToken(originalToken: String, otherAppId: String): String = "ON BEHALF OF!"
        override fun refreshTokens(refreshToken: String): JSONObject = JSONObject("""{"access_token":"abc","refresh_token":"cba"}""")
        override fun token(otherAppId: String): String = "token"
    }

    @BeforeEach
    fun setup() {
        MDC.put(XCorrelationId, "a request id")
        FuelManager.instance.client = object : Client {
            override fun executeRequest(request: Request): Response = okResponseFromInntekt()
        }
    }

    @AfterEach
    fun tearDown() {
        MDC.clear()
        FuelManager.instance.client = HttpClient(FuelManager.instance.proxy, hook = FuelManager.instance.hook)
    }

    private fun okResponseFromInntekt() = Response(
            url = URL("http://some.place"),
            contentLength = 0,
            headers = Headers(),
            responseMessage = "Thumbs up",
            statusCode = 200
    )
}