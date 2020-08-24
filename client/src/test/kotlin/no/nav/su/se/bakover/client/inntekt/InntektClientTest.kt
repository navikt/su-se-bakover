package no.nav.su.se.bakover.client.inntekt

import arrow.core.left
import arrow.core.right
import com.github.tomakehurst.wiremock.client.WireMock
import no.nav.su.se.bakover.client.ClientError
import no.nav.su.se.bakover.client.ClientResponse
import no.nav.su.se.bakover.client.WiremockBase
import no.nav.su.se.bakover.client.WiremockBase.Companion.wireMockServer
import no.nav.su.se.bakover.client.azure.OAuth
import no.nav.su.se.bakover.client.person.PersonOppslag
import no.nav.su.se.bakover.domain.AktørId
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.Ident
import no.nav.su.se.bakover.domain.Person
import no.nav.su.se.bakover.domain.Telefonnummer
import org.json.JSONObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.MDC

internal class InntektClientTest : WiremockBase {

    @Test
    fun `skal ikke kalle inntekt om person gir feil`() {
        val inntektClient = SuInntektClient(
            wireMockServer.baseUrl(),
            clientId,
            tokenExchange,
            persontilgang403
        )
        val result = inntektClient.inntekt(Fnr("01010112345"), "innlogget bruker", "2000-01", "2000-12")
        assertEquals(ClientResponse(403, "Du hakke lov"), result)
    }

    @Test
    fun `skal kalle inntekt om person gir OK`() {
        val inntektClient = SuInntektClient(
            wireMockServer.baseUrl(),
            clientId,
            tokenExchange,
            persontilgang200
        )
        val result = inntektClient.inntekt(Fnr("01010112345"), "innlogget bruker", "2000-01", "2000-12")
        assertEquals(ClientResponse(200, "{}"), result)
    }

    private val clientId = "inntektclientid"
    private val persontilgang200 = object : PersonOppslag {
        override fun person(fnr: Fnr) = Person(
            ident = Ident(fnr, AktørId("aktørId")),
            navn = Person.Navn(
                fornavn = "Tore",
                mellomnavn = "Johnas",
                etternavn = "Strømøy"
            ),
            telefonnummer = Telefonnummer(landskode = "47", nummer = "12345678"),
            adresse = Person.Adresse(
                adressenavn = "Oslogata",
                husnummer = "12",
                husbokstav = null,
                bruksenhet = "U1H20",
                poststed = Person.Poststed(postnummer = "0050", poststed = null),
                kommune = Person.Kommune(kommunenummer = "0301", kommunenavn = null)
            ),
            statsborgerskap = "NOR",
            kjønn = "MANN"

        ).right()
        override fun aktørId(fnr: Fnr) = AktørId("aktoerId").right()
    }
    private val persontilgang403 = object : PersonOppslag {
        override fun person(fnr: Fnr) =
            ClientError(403, "Du hakke lov").left()
        override fun aktørId(fnr: Fnr) = AktørId("aktoerId").right()
    }
    private val tokenExchange = object : OAuth {
        override fun onBehalfOFToken(originalToken: String, otherAppId: String): String = "ON BEHALF OF!"
        override fun refreshTokens(refreshToken: String): JSONObject = JSONObject("""{"access_token":"abc","refresh_token":"cba"}""")
        override fun jwkConfig() = JSONObject()
    }

    @BeforeEach
    fun setup() {
        MDC.put("X-Correlation-ID", "some UUID")
        wireMockServer.stubFor(
            WireMock.post(WireMock.urlPathEqualTo("/inntekt"))
                .willReturn(WireMock.okJson("""{}"""))
        )
    }
}
