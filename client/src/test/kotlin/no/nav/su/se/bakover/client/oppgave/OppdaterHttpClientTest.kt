package no.nav.su.se.bakover.client.oppgave

import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.equalToJson
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.patch
import com.github.tomakehurst.wiremock.client.WireMock.patchRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.auth.AzureAd
import no.nav.su.se.bakover.common.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.common.infrastructure.config.ApplicationConfig
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.oppgave.OppdaterOppgaveInfo
import no.nav.su.se.bakover.oppgave.domain.OppgaveHttpKallResponse
import no.nav.su.se.bakover.oppgave.domain.Oppgavetype
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.oppgave.nyOppgaveHttpKallResponse
import no.nav.su.se.bakover.test.wiremock.startedWireMockServerWithCorrelationId
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.skyscreamer.jsonassert.JSONAssert
import java.time.LocalDate
import java.time.ZonedDateTime
import java.util.UUID

/*
    Testene er en smule lite lesbart og prøve å mimicke oppgave på en litt rar måte.
    Men siden vi tidligere bare konkatenerte tekster i beskrivelsesfeltet vil det bli rart
    siden vi skal bruke kommentar attributtet for nye endringer mens beskrivelsen skal være det opprinnelige inneholdet.
 */
internal class OppdaterHttpClientTest {

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
        startedWireMockServerWithCorrelationId {
            val patchResponse = patchResponseForOppdaterOppgave()

            stubFor(get.willReturn(aResponse().withBody(getResponseForHentOppgave()).withStatus(200)))
            stubFor(patch.willReturn(aResponse().withBody(patchResponse).withStatus(200)))

            val oathMock = mock<AzureAd> { on { onBehalfOfToken(any(), any()) } doReturn "token" }
            val client = OppgaveHttpClient(
                connectionConfig = ApplicationConfig.ClientsConfig.OppgaveConfig(
                    clientId = "oppgaveClientId",
                    url = baseUrl(),
                ),
                exchange = oathMock,
                clock = fixedClock,
            )
            val actual = client.lukkOppgave(
                OppgaveId(oppgaveId.toString()),
                OppdaterOppgaveInfo.TilordnetRessurs.NavIdent("Z123456"),
            ).getOrFail()

            val beskrivelse = "Lukket av SU-app (Supplerende Stønad)"
            val expectedBody = patchRequestedBodyOppdaterOppgave(beskrivelse = beskrivelse)

            val expected = nyOppgaveHttpKallResponse(
                oppgaveId = OppgaveId(oppgaveId.toString()),
                oppgavetype = Oppgavetype.BEHANDLE_SAK,
                request = expectedBody,
                response = patchResponse,
                beskrivelse = beskrivelse,
                tilordnetRessurs = "Z123456",
            )

            actual.oppgaveId shouldBe expected.oppgaveId
            actual.oppgavetype shouldBe expected.oppgavetype
            actual.beskrivelse shouldBe expected.beskrivelse
            actual.response shouldBe expected.response
            actual.tilordnetRessurs shouldBe expected.tilordnetRessurs

            JSONAssert.assertEquals(actual.request, expected.request, true)

            WireMock.configureFor(port())
            WireMock.verify(1, patchRequested.withRequestBody(equalToJson(expectedBody)))
        }
    }

    @Test
    fun `lukker en oppgave med en oppgaveId for en systembruker`() {
        startedWireMockServerWithCorrelationId {
            val patchResponse = patchResponseForOppdaterOppgave()
            stubFor(get.willReturn(aResponse().withBody(getResponseForHentOppgave()).withStatus(200)))
            stubFor(patch.willReturn(aResponse().withBody(patchResponse).withStatus(200)))

            val oathMock = mock<AzureAd> { on { getSystemToken(any()) } doReturn "token" }

            val client = OppgaveHttpClient(
                connectionConfig = ApplicationConfig.ClientsConfig.OppgaveConfig(
                    clientId = "oppgaveClientId",
                    url = baseUrl(),
                ),
                exchange = oathMock,
                clock = fixedClock,
            )
            val actual = client.lukkOppgaveMedSystembruker(
                OppgaveId(oppgaveId.toString()),
                OppdaterOppgaveInfo.TilordnetRessurs.NavIdent("Z123456"),
            ).getOrFail()

            val beskrivelse = "Lukket av SU-app (Supplerende Stønad)"
            val expectedBody = patchRequestedBodyOppdaterOppgave(erObo = false, beskrivelse = beskrivelse)

            val expected = nyOppgaveHttpKallResponse(
                oppgaveId = OppgaveId(oppgaveId.toString()),
                oppgavetype = Oppgavetype.BEHANDLE_SAK,
                request = expectedBody,
                response = patchResponse,
                beskrivelse = beskrivelse,
                tilordnetRessurs = "Z123456",
            )

            actual.oppgaveId shouldBe expected.oppgaveId
            actual.oppgavetype shouldBe expected.oppgavetype
            actual.beskrivelse shouldBe expected.beskrivelse
            actual.response shouldBe expected.response
            actual.tilordnetRessurs shouldBe expected.tilordnetRessurs
            JSONAssert.assertEquals(actual.request, expected.request, true)

            WireMock.configureFor(port())
            WireMock.verify(1, patchRequested.withRequestBody(equalToJson(expectedBody)))
        }
    }

    @Test
    fun `Legger til lukket beskrivelse på starten av beskrivelse`() {
        startedWireMockServerWithCorrelationId {
            val patchResponse =
                patchResponseForOppdaterOppgave("\nSøknadId : $søknadId\n\n--- 01.01.0001 01:01 Fornavn Etternavn (Z12345, 4815) ---\nforrige melding")
            stubFor(
                get.willReturn(
                    aResponse().withBody(getResponseForHentOppgave("--- 01.01.0001 01:01 Fornavn Etternavn (Z12345, 4815) ---\\nforrige melding"))
                        .withStatus(200),
                ),
            )

            stubFor(patch.willReturn(aResponse().withBody(patchResponse).withStatus(200)))

            val oathMock = mock<AzureAd> { on { onBehalfOfToken(any(), any()) } doReturn "token" }
            val client = OppgaveHttpClient(
                connectionConfig = ApplicationConfig.ClientsConfig.OppgaveConfig(
                    clientId = "oppgaveClientId",
                    url = baseUrl(),
                ),
                exchange = oathMock,
                clock = fixedClock,
            )
            val actual = client.lukkOppgave(
                OppgaveId(oppgaveId.toString()),
                tilordnetRessurs = OppdaterOppgaveInfo.TilordnetRessurs.NavIdent("Z123456"),
            ).getOrFail()

            val expectedBody = patchRequestedBodyOppdaterOppgave(kommentar = "Lukket av SU-app (Supplerende Stønad)")
            val expected = nyOppgaveHttpKallResponse(
                oppgaveId = OppgaveId(oppgaveId.toString()),
                oppgavetype = Oppgavetype.BEHANDLE_SAK,
                request = expectedBody,
                beskrivelse = "Lukket av SU-app (Supplerende Stønad)",
                response = patchResponse,
                tilordnetRessurs = "Z123456",
            )

            actual.oppgaveId shouldBe expected.oppgaveId
            actual.oppgavetype shouldBe expected.oppgavetype
            actual.beskrivelse shouldBe expected.beskrivelse
            actual.response shouldBe expected.response
            actual.tilordnetRessurs shouldBe expected.tilordnetRessurs
            JSONAssert.assertEquals(actual.request, expected.request, true)

            WireMock.configureFor(port())
            WireMock.verify(1, patchRequested.withRequestBody(equalToJson(expectedBody)))
        }
    }

    @Test
    fun `oppdaterer eksisterende oppgave med ny beskrivelse`() {
        startedWireMockServerWithCorrelationId {
            val patchResponse = patchResponseForOppdaterOppgave("en beskrivelse")

            stubFor(
                get
                    .willReturn(
                        aResponse()
                            .withBody(getResponseForHentOppgave("Dette er den orginale beskrivelsen"))
                            .withStatus(200),
                    ),
            )

            stubFor(patch.willReturn(aResponse().withBody(patchResponse).withStatus(200)))

            val oauthMock = mock<AzureAd> { on { onBehalfOfToken(any(), any()) } doReturn "token" }

            val client = OppgaveHttpClient(
                connectionConfig = ApplicationConfig.ClientsConfig.OppgaveConfig(
                    clientId = "oppgaveClientId",
                    url = baseUrl(),
                ),
                exchange = oauthMock,
                clock = fixedClock,
            )

            val actual = client.oppdaterOppgave(
                oppgaveId = OppgaveId(oppgaveId.toString()),
                oppdatertOppgaveInfo = OppdaterOppgaveInfo(
                    beskrivelse = "en beskrivelse",
                    oppgavetype = Oppgavetype.BEHANDLE_SAK,
                    status = null,
                    tilordnetRessurs = OppdaterOppgaveInfo.TilordnetRessurs.NavIdent("Z123456"),
                ),
            ).getOrFail()

            val expectedBody = patchRequestedBodyOppdaterOppgave(
                kommentar = "en beskrivelse",
                status = "AAPNET",
            )

            val expected = nyOppgaveHttpKallResponse(
                oppgaveId = OppgaveId(oppgaveId.toString()),
                oppgavetype = Oppgavetype.BEHANDLE_SAK,
                request = expectedBody,
                response = patchResponse,
                beskrivelse = "en beskrivelse",
                tilordnetRessurs = "Z123456",
            )

            actual.oppgaveId shouldBe expected.oppgaveId
            actual.oppgavetype shouldBe expected.oppgavetype
            actual.beskrivelse shouldBe expected.beskrivelse
            actual.response shouldBe expected.response
            actual.tilordnetRessurs shouldBe expected.tilordnetRessurs
            JSONAssert.assertEquals(actual.request, expected.request, true)

            WireMock.configureFor(port())
            WireMock.verify(1, patchRequested.withRequestBody(equalToJson(expectedBody)))
        }
    }

    private data class EndreOppgaveRequest(
        val beskrivelse: String?,
        val kommentar: Kommentar? = null,
        val oppgavetype: String,
        val status: String,
        val tilordnetRessurs: String?,
        val endretAvEnhetsnr: String?,
    )

    private data class Kommentar(
        val tekst: String,
    )

    private fun patchRequestedBodyOppdaterOppgave(
        beskrivelse: String? = null,
        status: String = "FERDIGSTILT",
        erObo: Boolean = false,
        kommentar: String? = null,
    ): String {
        val request = EndreOppgaveRequest(
            beskrivelse = beskrivelse,
            status = status,
            oppgavetype = "BEH_SAK",
            tilordnetRessurs = if (erObo) null else "Z123456",
            endretAvEnhetsnr = "4815",
            kommentar = kommentar?.let { Kommentar(tekst = it) },
        )
        return serialize(request)
    }

    private fun patchResponseForOppdaterOppgave(
        beskrivelse: String = "Lukket av SU-app (Supplerende Stønad) ---\nSøknadId : $søknadId",
    ): String {
        val response = OppgaveHttpKallResponse(
            oppgaveId = OppgaveId(oppgaveId.toString()),
            beskrivelse = beskrivelse,
            tilordnetRessurs = "Z123456",
            oppgavetype = Oppgavetype.BEHANDLE_SAK,
            request = "",
            response = "",
            tildeltEnhetsnr = "4815",
        )
        return serialize(response)
    }

    private fun getResponseForHentOppgave(beskrivelse: String? = null): String {
        val response = OppgaveResponse(
            id = oppgaveId,
            tildeltEnhetsnr = "1234",
            journalpostId = null,
            saksreferanse = søknadId.toString(),
            aktoerId = "1000012345678",
            beskrivelse = beskrivelse,
            tema = "SUP",
            behandlingstema = null,
            oppgavetype = "BEH_SAK",
            tilordnetRessurs = "Z123456",
            behandlingstype = "ae0034",
            versjon = versjon,
            opprettetAv = "supstonad",
            prioritet = "NORM",
            status = "AAPNET",
            metadata = emptyMap<String, Any>(),
            fristFerdigstillelse = LocalDate.parse("2019-01-04"),
            aktivDato = LocalDate.parse("2019-01-04"),
            opprettetTidspunkt = ZonedDateTime.parse("2019-01-04T09:53:39.329+02:02"),
            ferdigstiltTidspunkt = null,
            endretAv = "supstonad",
        )

        return serialize(response)
    }
}
