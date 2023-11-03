package no.nav.su.se.bakover.client.oppgave

import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.equalToJson
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.patch
import com.github.tomakehurst.wiremock.client.WireMock.patchRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.client.AccessToken
import no.nav.su.se.bakover.client.WiremockBase
import no.nav.su.se.bakover.client.WiremockBase.Companion.wireMockServer
import no.nav.su.se.bakover.client.sts.TokenOppslag
import no.nav.su.se.bakover.common.auth.AzureAd
import no.nav.su.se.bakover.common.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.common.infrastructure.config.ApplicationConfig
import no.nav.su.se.bakover.oppgave.domain.Oppgavetype
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.oppgave.nyOppgaveHttpKallResponse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.skyscreamer.jsonassert.JSONAssert
import org.slf4j.MDC
import java.util.UUID

internal class OppdaterHttpClientTest : WiremockBase {

    private val søknadId = UUID.randomUUID()
    private val oppgaveId = 12345L
    private val versjon = 2

    private val patch = patch((urlPathEqualTo("$OPPGAVE_PATH/$oppgaveId")))
        .withHeader("Authorization", WireMock.equalTo("Bearer token"))
        .withHeader("Content-Type", WireMock.equalTo("application/json"))
        .withHeader("Accept", WireMock.equalTo("application/json"))
        .withHeader("X-Correlation-ID", WireMock.equalTo("correlationId"))
    private val get = get((urlPathEqualTo("$OPPGAVE_PATH/$oppgaveId")))
        .withHeader("Authorization", WireMock.equalTo("Bearer token"))
        .withHeader("Content-Type", WireMock.equalTo("application/json"))
        .withHeader("Accept", WireMock.equalTo("application/json"))
        .withHeader("X-Correlation-ID", WireMock.equalTo("correlationId"))

    private val patchRequested = patchRequestedFor(urlPathEqualTo("$OPPGAVE_PATH/$oppgaveId"))
        .withHeader("Authorization", WireMock.equalTo("Bearer token"))
        .withHeader("Content-Type", WireMock.equalTo("application/json"))
        .withHeader("Accept", WireMock.equalTo("application/json"))
        .withHeader("X-Correlation-ID", WireMock.equalTo("correlationId"))

    @Test
    fun `lukker en oppgave med en oppgaveId`() {
        val patchResponse = createJsonPatchResponse()

        wireMockServer.stubFor(get.willReturn(aResponse().withBody(createJsonGetResponse()).withStatus(200)))
        wireMockServer.stubFor(patch.willReturn(aResponse().withBody(patchResponse).withStatus(200)))

        val oathMock = mock<AzureAd> { on { onBehalfOfToken(any(), any()) } doReturn "token" }
        val client = OppgaveHttpClient(
            connectionConfig = ApplicationConfig.ClientsConfig.OppgaveConfig(
                clientId = "oppgaveClientId",
                url = wireMockServer.baseUrl(),
            ),
            exchange = oathMock,
            tokenoppslagForSystembruker = mock(),
            clock = fixedClock,
        )
        val actual = client.lukkOppgave(OppgaveId(oppgaveId.toString())).getOrFail()

        val expectedBody = createJsonPatchRequestedBody()

        val expected = nyOppgaveHttpKallResponse(
            oppgaveId = OppgaveId(oppgaveId.toString()),
            oppgavetype = Oppgavetype.BEHANDLE_SAK,
            request = expectedBody,
            response = patchResponse,
            beskrivelse = "Lukket av SU-app (Supplerende Stønad)",
        )

        actual.oppgaveId shouldBe expected.oppgaveId
        actual.oppgavetype shouldBe expected.oppgavetype
        actual.beskrivelse shouldBe expected.beskrivelse
        actual.response shouldBe expected.response
        JSONAssert.assertEquals(actual.request, expected.request, true)

        WireMock.configureFor(wireMockServer.port())
        WireMock.verify(1, patchRequested.withRequestBody(equalToJson(expectedBody)))
    }

    @Test
    fun `lukker en oppgave med en oppgaveId for en systembruker`() {
        val patchResponse = createJsonPatchResponse()
        wireMockServer.stubFor(get.willReturn(aResponse().withBody(createJsonGetResponse()).withStatus(200)))
        wireMockServer.stubFor(patch.willReturn(aResponse().withBody(patchResponse).withStatus(200)))

        val tokenoppslagMock = mock<TokenOppslag> { on { token() } doReturn AccessToken("token") }

        val client = OppgaveHttpClient(
            connectionConfig = ApplicationConfig.ClientsConfig.OppgaveConfig(
                clientId = "oppgaveClientId",
                url = wireMockServer.baseUrl(),
            ),
            exchange = mock(),
            tokenoppslagForSystembruker = tokenoppslagMock,
            clock = fixedClock,
        )
        val actual = client.lukkOppgaveMedSystembruker(OppgaveId(oppgaveId.toString())).getOrFail()

        val expectedBody = createJsonPatchRequestedBody()

        val expected = nyOppgaveHttpKallResponse(
            oppgaveId = OppgaveId(oppgaveId.toString()),
            oppgavetype = Oppgavetype.BEHANDLE_SAK,
            request = expectedBody,
            response = patchResponse,
            beskrivelse = "Lukket av SU-app (Supplerende Stønad)",
        )

        actual.oppgaveId shouldBe expected.oppgaveId
        actual.oppgavetype shouldBe expected.oppgavetype
        actual.beskrivelse shouldBe expected.beskrivelse
        actual.response shouldBe expected.response
        JSONAssert.assertEquals(actual.request, expected.request, true)

        WireMock.configureFor(wireMockServer.port())
        WireMock.verify(1, patchRequested.withRequestBody(equalToJson(expectedBody)))
    }

    @Test
    fun `Legger til lukket beskrivelse på starten av beskrivelse`() {
        val patchResponse =
            createJsonPatchResponse("--- 01.01.2021 02:02 - Lukket av SU-app (Supplerende Stønad) ---\nSøknadId : $søknadId\n\n--- 01.01.0001 01:01 Fornavn Etternavn (Z12345, 4815) ---\nforrige melding")
        wireMockServer.stubFor(
            get.willReturn(
                aResponse().withBody(createJsonGetResponse("--- 01.01.0001 01:01 Fornavn Etternavn (Z12345, 4815) ---\\nforrige melding"))
                    .withStatus(200),
            ),
        )

        wireMockServer.stubFor(patch.willReturn(aResponse().withBody(patchResponse).withStatus(200)))

        val oathMock = mock<AzureAd> { on { onBehalfOfToken(any(), any()) } doReturn "token" }
        val client = OppgaveHttpClient(
            connectionConfig = ApplicationConfig.ClientsConfig.OppgaveConfig(
                clientId = "oppgaveClientId",
                url = wireMockServer.baseUrl(),
            ),
            exchange = oathMock,
            tokenoppslagForSystembruker = mock(),
            clock = fixedClock,
        )
        val actual = client.lukkOppgave(OppgaveId(oppgaveId.toString())).getOrFail()

        val expectedBody =
            createJsonPatchRequestedBody("""--- 01.01.2021 02:02 - Lukket av SU-app (Supplerende Stønad) ---\n\n--- 01.01.0001 01:01 Fornavn Etternavn (Z12345, 4815) ---\nforrige melding""")

        val expected = nyOppgaveHttpKallResponse(
            oppgaveId = OppgaveId(oppgaveId.toString()),
            oppgavetype = Oppgavetype.BEHANDLE_SAK,
            request = expectedBody,
            response = patchResponse,
            beskrivelse = "Lukket av SU-app (Supplerende Stønad)",
        )

        actual.oppgaveId shouldBe expected.oppgaveId
        actual.oppgavetype shouldBe expected.oppgavetype
        actual.beskrivelse shouldBe expected.beskrivelse
        actual.response shouldBe expected.response
        JSONAssert.assertEquals(actual.request, expected.request, true)

        WireMock.configureFor(wireMockServer.port())
        WireMock.verify(1, patchRequested.withRequestBody(equalToJson(expectedBody)))
    }

    @Test
    fun `oppdaterer eksisterende oppgave med ny beskrivelse`() {
        val patchResponse = createJsonPatchResponse("--- 01.01.2021 02:02 - en beskrivelse ---", "AAPNET")

        wireMockServer.stubFor(
            get
                .willReturn(
                    aResponse()
                        .withBody(createJsonGetResponse("Dette er den orginale beskrivelsen"))
                        .withStatus(200),
                ),
        )

        wireMockServer.stubFor(patch.willReturn(aResponse().withBody(patchResponse).withStatus(200)))

        val oauthMock = mock<AzureAd> { on { onBehalfOfToken(any(), any()) } doReturn "token" }

        val client = OppgaveHttpClient(
            connectionConfig = ApplicationConfig.ClientsConfig.OppgaveConfig(
                clientId = "oppgaveClientId",
                url = wireMockServer.baseUrl(),
            ),
            exchange = oauthMock,
            tokenoppslagForSystembruker = mock(),
            clock = fixedClock,
        )

        val actual = client.oppdaterOppgave(oppgaveId = OppgaveId(oppgaveId.toString()), beskrivelse = "en beskrivelse")
            .getOrFail()

        val expectedBody = createJsonPatchRequestedBody(
            """--- 01.01.2021 02:02 - en beskrivelse ---\n\nDette er den orginale beskrivelsen""",
            "AAPNET",
        )

        val expected = nyOppgaveHttpKallResponse(
            oppgaveId = OppgaveId(oppgaveId.toString()),
            oppgavetype = Oppgavetype.BEHANDLE_SAK,
            request = expectedBody,
            response = patchResponse,
            beskrivelse = "en beskrivelse",
        )

        actual.oppgaveId shouldBe expected.oppgaveId
        actual.oppgavetype shouldBe expected.oppgavetype
        actual.beskrivelse shouldBe expected.beskrivelse
        actual.response shouldBe expected.response
        JSONAssert.assertEquals(actual.request, expected.request, true)

        WireMock.configureFor(wireMockServer.port())
        WireMock.verify(1, patchRequested.withRequestBody(equalToJson(expectedBody)))
    }

    private fun createJsonPatchRequestedBody(
        beskrivelse: String = "--- 01.01.2021 02:02 - Lukket av SU-app (Supplerende Stønad) ---",
        status: String = "FERDIGSTILT",
    ): String {
        //language=json
        return """
            {
              "oppgavetype": "BEH_SAK",
              "beskrivelse": "$beskrivelse",
              "status": "$status"
            }
        """.trimIndent()
    }

    private fun createJsonPatchResponse(
        beskrivelse: String = "--- 01.01.2021 02:02 - Lukket av SU-app (Supplerende Stønad) ---\nSøknadId : $søknadId",
        status: String = "FERDIGSTILT",
    ): String {
        //language=json
        return """
                {
                  "id": $oppgaveId,
                  "versjon": ${versjon + 1},
                  "beskrivelse": "$beskrivelse",
                  "status": "$status"
                }
        """.trimIndent()
    }

    private fun createJsonGetResponse(beskrivelse: String? = null): String {
        //language=json
        return """
                {
                  "id": $oppgaveId,
                  "tildeltEnhetsnr": "1234",
                  "endretAvEnhetsnr": "1234",
                  "opprettetAvEnhetsnr": "1234",
                  "aktoerId": "1000012345678",
                  "saksreferanse": "$søknadId",
                  "tilordnetRessurs": "Z123456",
                  "tema": "SUP",
                  "beskrivelse": ${beskrivelse?.let { "\"$it\"" }},
                  "oppgavetype": "BEH_SAK",
                  "behandlingstype": "ae0034",
                  "versjon": $versjon,
                  "opprettetAv": "supstonad",
                  "endretAv": "supstonad",
                  "prioritet": "NORM",
                  "status": "AAPNET",
                  "metadata": {},
                  "fristFerdigstillelse": "2019-01-04",
                  "aktivDato": "2019-01-04",
                  "opprettetTidspunkt": "2019-01-04T09:53:39.329+02:02",
                  "endretTidspunkt": "2019-08-25T11:45:38+02:00"
                }
        """.trimIndent()
    }

    @BeforeEach
    fun beforeEach() {
        MDC.put("Authorization", "Bearer token")
    }
}
