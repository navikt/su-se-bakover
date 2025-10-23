package no.nav.su.se.bakover.client.oppgave

import arrow.core.left
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.equalToJson
import com.github.tomakehurst.wiremock.client.WireMock.forbidden
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.http.Fault
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.client.argThat
import no.nav.su.se.bakover.common.auth.AzureAd
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.domain.kodeverk.Behandlingstema
import no.nav.su.se.bakover.common.domain.kodeverk.Tema
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.ident.NavIdentBruker.Saksbehandler
import no.nav.su.se.bakover.common.infrastructure.config.ApplicationConfig
import no.nav.su.se.bakover.common.journal.JournalpostId
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.klage.AvsluttetKlageinstansUtfall
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import no.nav.su.se.bakover.oppgave.domain.KunneIkkeOppretteOppgave
import no.nav.su.se.bakover.oppgave.domain.OppgaveHttpKallResponse
import no.nav.su.se.bakover.oppgave.domain.Oppgavetype
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.fnr
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.oppgave.nyOppgaveHttpKallResponse
import no.nav.su.se.bakover.test.oppgave.oppgaveId
import no.nav.su.se.bakover.test.wiremock.startedWireMockServerWithCorrelationId
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.skyscreamer.jsonassert.JSONAssert
import java.time.LocalDate
import java.time.ZonedDateTime

internal class OppgaveHttpClientTest {

    private val saksbehandler = Saksbehandler("Z12345")
    private val aktørId = "333"
    private val journalpostId = JournalpostId("444")
    private val saksnummer = Saksnummer(12345)
    private val clientId = "oppgaveClientId"
    private val bearertoken = "Bearer token"

    private fun Sakstype.toBehandlingstema(): Behandlingstema =
        when (this) {
            Sakstype.ALDER -> Behandlingstema.SU_ALDER
            Sakstype.UFØRE -> Behandlingstema.SU_UFØRE_FLYKTNING
        }
    data class OppgaveOgAzure(
        val client: OppgaveHttpClient,
        val azure: AzureAd,
    )

    private fun createOppgaveClientWithAzure(
        azureAdMock: AzureAd = mock<AzureAd> { on { onBehalfOfToken(any(), any()) } doReturn "token" },
        baseUrl: String,
    ): OppgaveOgAzure {
        val client = OppgaveHttpClient(
            connectionConfig = ApplicationConfig.ClientsConfig.OppgaveConfig(
                clientId = clientId,
                url = baseUrl,
            ),
            exchange = azureAdMock,
            clock = fixedClock,
        )

        return OppgaveOgAzure(client, azureAdMock)
    }

    private fun WireMockServer.stubOppgave(expectedBody: String, response: String, status: Int = 201) {
        stubFor(
            stubMapping.withRequestBody(equalToJson(expectedBody))
                .willReturn(aResponse().withBody(response).withStatus(status)),
        )
    }

    @Test
    fun `oppretter oppgave for saksbehandler`() {
        val sakstype = Sakstype.ALDER
        startedWireMockServerWithCorrelationId {
            val oppgave = OppgaveConfig.Søknad(
                sakstype = sakstype,
                journalpostId = journalpostId,
                saksnummer = saksnummer,
                fnr = fnr,
                tilordnetRessurs = saksbehandler,
                clock = fixedClock,
            )
            val expectedSaksbehandlingRequest = createOppgaveRequest(journalpostId = journalpostId, tilordnetRessurs = saksbehandler, behandlingstema = sakstype.toBehandlingstema(), beskrivelse = oppgave.beskrivelse)
            val response = createResponse(beskrivelse = oppgave.beskrivelse)
            stubOppgave(expectedSaksbehandlingRequest, response)

            val clientogAzure = createOppgaveClientWithAzure(baseUrl = baseUrl())
            val actual = clientogAzure.client.opprettOppgave(
                oppgave,
            ).getOrFail()

            val expected = nyOppgaveHttpKallResponse(
                oppgaveId = oppgaveId,
                oppgavetype = Oppgavetype.BEHANDLE_SAK,
                request = expectedSaksbehandlingRequest,
                response = response,
                beskrivelse = oppgave.beskrivelse,
            )

            verify(clientogAzure.azure).onBehalfOfToken(
                originalToken = argThat { it shouldBe bearertoken },
                otherAppId = argThat { it shouldBe clientId },
            )
            verifyNoMoreInteractions(clientogAzure.azure)

            assertOppgaveEquals(actual, expected)
        }
    }

    @Test
    fun `opprett oppgave med systembruker`() {
        val sakstype = Sakstype.ALDER
        startedWireMockServerWithCorrelationId {
            val oppgave = OppgaveConfig.Søknad(
                sakstype = sakstype,
                journalpostId = journalpostId,
                saksnummer = saksnummer,
                fnr = fnr,
                tilordnetRessurs = null,
                clock = fixedClock,
            )
            val expectedSaksbehandlingRequest = createOppgaveRequest(journalpostId = journalpostId, behandlingstema = sakstype.toBehandlingstema(), beskrivelse = oppgave.beskrivelse)
            val response = createResponse(beskrivelse = oppgave.beskrivelse)

            stubOppgave(expectedSaksbehandlingRequest, response)

            val expected = nyOppgaveHttpKallResponse(
                oppgaveId = oppgaveId,
                oppgavetype = Oppgavetype.BEHANDLE_SAK,
                request = expectedSaksbehandlingRequest,
                response = response,
                beskrivelse = oppgave.beskrivelse,
            )

            val clientogAzure = createOppgaveClientWithAzure(baseUrl = baseUrl(), azureAdMock = mock<AzureAd> { on { getSystemToken(any()) } doReturn "token" })
            val actual = clientogAzure.client.opprettOppgaveMedSystembruker(
                oppgave,
            ).getOrFail()

            verify(clientogAzure.azure).getSystemToken(any())
            verifyNoMoreInteractions(clientogAzure.azure)
            assertOppgaveEquals(actual, expected)
        }
    }

    @Test
    fun `opprett attestering oppgave`() {
        val sakstype = Sakstype.ALDER
        startedWireMockServerWithCorrelationId {
            val oppgave = OppgaveConfig.AttesterSøknadsbehandling(
                saksnummer = saksnummer,
                fnr = fnr,
                clock = fixedClock,
                tilordnetRessurs = null,
                sakstype = sakstype,
            )
            val expectedAttesteringRequest = createOppgaveRequest(journalpostId = null, oppgavetype = oppgave.oppgavetype.value, behandlingstema = sakstype.toBehandlingstema(), saksreferanse = saksnummer.toString(), beskrivelse = oppgave.beskrivelse)
            val response = createResponse(oppgavetype = oppgave.oppgavetype.value, beskrivelse = oppgave.beskrivelse)
            stubOppgave(expectedAttesteringRequest, response)

            val clientogAzure = createOppgaveClientWithAzure(baseUrl = baseUrl())
            val actual = clientogAzure.client.opprettOppgave(
                oppgave,
            ).getOrFail()

            val expected = nyOppgaveHttpKallResponse(
                oppgaveId = oppgaveId,
                oppgavetype = Oppgavetype.ATTESTERING,
                request = expectedAttesteringRequest,
                response = response,
                beskrivelse = oppgave.beskrivelse,
            )

            assertOppgaveEquals(actual, expected)
        }
    }

    @Test
    fun `returns KunneIkkeOppretteOppgave`() {
        startedWireMockServerWithCorrelationId {
            stubFor(stubMapping.willReturn(forbidden()))

            val clientogAzure = createOppgaveClientWithAzure(baseUrl = baseUrl())

            clientogAzure.client.opprettOppgave(
                OppgaveConfig.Søknad(
                    sakstype = Sakstype.UFØRE,
                    journalpostId = journalpostId,
                    saksnummer = saksnummer,
                    fnr = fnr,
                    clock = fixedClock,
                    tilordnetRessurs = null,
                ),
            ) shouldBe KunneIkkeOppretteOppgave.left()
        }
    }

    @Test
    fun `oppretter en saksbehandling for en revurdering`() {
        val sakstype = Sakstype.ALDER
        startedWireMockServerWithCorrelationId {
            val oppgave = OppgaveConfig.Revurderingsbehandling(
                saksnummer = saksnummer,
                fnr = fnr,
                tilordnetRessurs = null,
                clock = fixedClock,
                sakstype = sakstype,
            )

            val expectedSaksbehandlingRequest = createOppgaveRequest(
                journalpostId = null,
                saksreferanse = saksnummer.toString(),
                beskrivelse = oppgave.beskrivelse,
                behandlingstype = "ae0028",
                behandlingstema = sakstype.toBehandlingstema(),
            )
            val response = createResponse(beskrivelse = oppgave.beskrivelse)

            stubOppgave(expectedSaksbehandlingRequest, response)

            val clientogAzure = createOppgaveClientWithAzure(baseUrl = baseUrl())

            val actual = clientogAzure.client.opprettOppgave(
                oppgave,
            ).getOrFail()

            val expected = nyOppgaveHttpKallResponse(
                oppgaveId = oppgaveId,
                oppgavetype = Oppgavetype.BEHANDLE_SAK,
                request = expectedSaksbehandlingRequest,
                response = response,
                beskrivelse = oppgave.beskrivelse,
            )

            verify(clientogAzure.azure).onBehalfOfToken(
                originalToken = argThat { it shouldBe bearertoken },
                otherAppId = argThat { it shouldBe clientId },
            )
            verifyNoMoreInteractions(clientogAzure.azure)

            assertOppgaveEquals(actual, expected)
        }
    }

    @Test
    fun `oppretter en saksbehandling for en revurdering med systembruker`() {
        val sakstype = Sakstype.ALDER
        startedWireMockServerWithCorrelationId {
            val oppgave = OppgaveConfig.Revurderingsbehandling(
                saksnummer = saksnummer,
                fnr = fnr,
                tilordnetRessurs = null,
                clock = fixedClock,
                sakstype = sakstype,
            )
            val expectedSaksbehandlingRequest = createOppgaveRequest(
                journalpostId = null,
                saksreferanse = saksnummer.toString(),
                beskrivelse = oppgave.beskrivelse,
                behandlingstype = "ae0028",
                behandlingstema = sakstype.toBehandlingstema(),
            )
            val response = createResponse(beskrivelse = oppgave.beskrivelse)

            stubOppgave(expectedSaksbehandlingRequest, response)

            val expected = nyOppgaveHttpKallResponse(
                oppgaveId = oppgaveId,
                oppgavetype = Oppgavetype.BEHANDLE_SAK,
                request = expectedSaksbehandlingRequest,
                response = response,
                beskrivelse = oppgave.beskrivelse,
            )

            val clientogAzure = createOppgaveClientWithAzure(baseUrl = baseUrl(), azureAdMock = mock<AzureAd> { on { getSystemToken(any()) } doReturn "token" })

            val actual = clientogAzure.client.opprettOppgaveMedSystembruker(
                oppgave,
            ).getOrFail()

            verify(clientogAzure.azure).getSystemToken(any())
            verifyNoMoreInteractions(clientogAzure.azure)

            assertOppgaveEquals(actual, expected)
        }
    }

    @Test
    fun `opprett attestering oppgave for revurdering`() {
        val sakstype = Sakstype.ALDER
        startedWireMockServerWithCorrelationId {
            val oppgave = OppgaveConfig.AttesterRevurdering(
                saksnummer = saksnummer,
                fnr = fnr,
                tilordnetRessurs = null,
                clock = fixedClock,
                sakstype = sakstype,
            )

            val expectedAttesteringRequest = createOppgaveRequest(
                journalpostId = null,
                saksreferanse = saksnummer.toString(),
                beskrivelse = oppgave.beskrivelse,
                oppgavetype = oppgave.oppgavetype.value,
                behandlingstype = "ae0028",
                behandlingstema = sakstype.toBehandlingstema(),
            )

            val response = createResponse(
                beskrivelse = oppgave.beskrivelse,
                oppgavetype = oppgave.oppgavetype.value,
                behandlingstype = "ae0028",
            )
            stubOppgave(expectedAttesteringRequest, response)

            val clientogAzure = createOppgaveClientWithAzure(baseUrl = baseUrl())
            val actual = clientogAzure.client.opprettOppgave(
                oppgave,
            ).getOrFail()

            val expected = nyOppgaveHttpKallResponse(
                oppgaveId = oppgaveId,
                oppgavetype = Oppgavetype.ATTESTERING,
                request = expectedAttesteringRequest,
                response = response,
                beskrivelse = oppgave.beskrivelse,
            )

            assertOppgaveEquals(actual, expected)
        }
    }

    @Test
    fun `opprett oppgave feiler med connection reset by peer`() {
        startedWireMockServerWithCorrelationId {
            stubFor(
                WireMock.post(urlPathEqualTo(OPPGAVE_PATH))
                    .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)),
            )

            val clientogAzure = createOppgaveClientWithAzure(baseUrl = baseUrl())

            clientogAzure.client.opprettOppgave(
                OppgaveConfig.AttesterRevurdering(
                    saksnummer = saksnummer,
                    fnr = fnr,
                    tilordnetRessurs = null,
                    clock = fixedClock,
                    sakstype = Sakstype.UFØRE,
                ),
            ) shouldBe KunneIkkeOppretteOppgave.left()
        }
    }

    @Test
    fun `oppretter STADFESTELSE-oppgave for klageinstanshendelse`() {
        val sakstype = Sakstype.ALDER
        startedWireMockServerWithCorrelationId {
            val oppgave = OppgaveConfig.Klage.Klageinstanshendelse.AvsluttetKlageinstansUtfall.Informasjon(
                saksnummer = saksnummer,
                fnr = fnr,
                tilordnetRessurs = null,
                clock = fixedClock,
                utfall = AvsluttetKlageinstansUtfall.TilInformasjon.Stadfestelse,
                avsluttetTidspunkt = fixedTidspunkt,
                journalpostIDer = listOf(JournalpostId("123"), JournalpostId("456")),
                hendelsestype = "enHendelse",
                sakstype = sakstype,
            )
            val expectedAttesteringRequest = createOppgaveRequest(
                journalpostId = null,
                saksreferanse = saksnummer.toString(),
                beskrivelse = oppgave.beskrivelse,
                oppgavetype = oppgave.oppgavetype.value,
                behandlingstype = "ae0058",
                behandlingstema = sakstype.toBehandlingstema(),
            )
            val response = createResponse(oppgavetype = oppgave.oppgavetype.value, beskrivelse = oppgave.beskrivelse)

            stubOppgave(expectedAttesteringRequest, response)

            val clientogAzure = createOppgaveClientWithAzure(baseUrl = baseUrl())

            val actual = clientogAzure.client.opprettOppgave(
                oppgave,
            ).getOrFail()

            val expected = nyOppgaveHttpKallResponse(
                oppgaveId = oppgaveId,
                oppgavetype = Oppgavetype.VURDER_KONSEKVENS_FOR_YTELSE,
                request = expectedAttesteringRequest,
                response = response,
                beskrivelse = oppgave.beskrivelse,
            )

            assertOppgaveEquals(actual, expected)
        }
    }

    @Test
    fun `oppretter MEDHOLD-oppgave for klageinstanshendelse`() {
        val sakstype = Sakstype.ALDER
        startedWireMockServerWithCorrelationId {
            val oppgave = OppgaveConfig.Klage.Klageinstanshendelse.AvsluttetKlageinstansUtfall.Handling(
                saksnummer = saksnummer,
                fnr = fnr,
                tilordnetRessurs = null,
                clock = fixedClock,
                utfall = AvsluttetKlageinstansUtfall.Retur,
                avsluttetTidspunkt = fixedTidspunkt,
                journalpostIDer = listOf(JournalpostId("123"), JournalpostId("456")),
                hendelsestype = "enHendelse",
                sakstype = sakstype,
            )

            val expectedAttesteringRequest = createOppgaveRequest(
                journalpostId = null,
                saksreferanse = saksnummer.toString(),
                beskrivelse = oppgave.beskrivelse,
                behandlingstype = "ae0058",
                behandlingstema = sakstype.toBehandlingstema(),
            )
            val response = createResponse(oppgavetype = oppgave.oppgavetype.value, beskrivelse = oppgave.beskrivelse)

            stubOppgave(expectedAttesteringRequest, response)

            val clientogAzure = createOppgaveClientWithAzure(baseUrl = baseUrl())

            val actual = clientogAzure.client.opprettOppgave(
                oppgave,
            ).getOrFail()

            val expected = nyOppgaveHttpKallResponse(
                oppgaveId = oppgaveId,
                oppgavetype = Oppgavetype.BEHANDLE_SAK,
                request = expectedAttesteringRequest,
                response = response,
                beskrivelse = oppgave.beskrivelse,
            )

            assertOppgaveEquals(actual, expected)
        }
    }

    @Test
    fun `oppretter RETUR-oppgave for klageinstanshendelse`() {
        val sakstype = Sakstype.ALDER
        startedWireMockServerWithCorrelationId {
            val oppgave = OppgaveConfig.Klage.Klageinstanshendelse.AvsluttetKlageinstansUtfall.Handling(
                saksnummer = saksnummer,
                fnr = fnr,
                tilordnetRessurs = null,
                clock = fixedClock,
                utfall = AvsluttetKlageinstansUtfall.Retur,
                avsluttetTidspunkt = fixedTidspunkt,
                journalpostIDer = listOf(JournalpostId("123"), JournalpostId("456")),
                hendelsestype = "enHendelse",
                sakstype = sakstype,
            )

            val expectedAttesteringRequest = createOppgaveRequest(
                journalpostId = null,
                saksreferanse = saksnummer.toString(),
                beskrivelse = oppgave.beskrivelse,
                behandlingstype = "ae0058",
                behandlingstema = sakstype.toBehandlingstema(),
            )

            val response = createResponse(oppgavetype = oppgave.oppgavetype.value, beskrivelse = oppgave.beskrivelse)

            stubOppgave(expectedAttesteringRequest, response)

            val clientogAzure = createOppgaveClientWithAzure(baseUrl = baseUrl())

            val actual = clientogAzure.client.opprettOppgave(
                oppgave,
            ).getOrFail()

            val expected = nyOppgaveHttpKallResponse(
                oppgaveId = oppgaveId,
                oppgavetype = Oppgavetype.BEHANDLE_SAK,
                request = expectedAttesteringRequest,
                response = response,
                beskrivelse = oppgave.beskrivelse,
            )

            assertOppgaveEquals(actual, expected)
        }
    }

    private fun assertOppgaveEquals(actual: OppgaveHttpKallResponse, expected: OppgaveHttpKallResponse) {
        actual.oppgaveId shouldBe expected.oppgaveId
        actual.oppgavetype shouldBe expected.oppgavetype
        actual.beskrivelse shouldBe expected.beskrivelse
        actual.response shouldBe expected.response
        JSONAssert.assertEquals(expected.request, actual.request, true)
    }

    private fun createOppgaveRequest(
        journalpostId: JournalpostId? = null,
        beskrivelse: String,
        saksreferanse: String = saksnummer.toString(),
        oppgavetype: String = "BEH_SAK",
        behandlingstype: String = "ae0034",
        behandlingstema: Behandlingstema?,
        aktivDato: LocalDate = LocalDate.of(2021, 1, 1),
        fristFerdigstillelse: LocalDate = LocalDate.of(2021, 1, 31),
        prioritet: String = "NORM",
        tilordnetRessurs: Saksbehandler? = null,
    ): String {
        return serialize(
            OppgaveRequest(
                journalpostId = journalpostId?.toString(),
                saksreferanse = saksnummer.toString(),
                personident = fnr.toString(),
                tema = Tema.SUPPLERENDE_STØNAD.value,
                beskrivelse = beskrivelse,
                oppgavetype = oppgavetype,
                behandlingstema = behandlingstema?.toString(),
                behandlingstype = behandlingstype,
                aktivDato = aktivDato,
                fristFerdigstillelse = fristFerdigstillelse,
                prioritet = prioritet,
                tilordnetRessurs = tilordnetRessurs?.toString(),
                tildeltEnhetsnr = tilordnetRessurs?.let { "4815" },
            ),
        )
    }

    private fun createResponse(
        tilordnetResurs: Saksbehandler? = null,
        beskrivelse: String,
        oppgavetype: String = "BEH_SAK",
        behandlingstype: String = "ae0034",
    ): String {
        val response = OppgaveResponse(
            id = 123,
            tildeltEnhetsnr = "4815",
            journalpostId = journalpostId.toString(),
            saksreferanse = saksnummer.toString(),
            aktoerId = aktørId,
            beskrivelse = beskrivelse,
            tema = "SUP",
            behandlingstema = "ab0431",
            oppgavetype = oppgavetype,
            tilordnetRessurs = tilordnetResurs?.toString(),
            behandlingstype = behandlingstype,
            versjon = 1,
            opprettetAv = "srvsupstonad",
            prioritet = "NORM",
            status = "OPPRETTET",
            metadata = emptyMap<String, String>(),
            fristFerdigstillelse = LocalDate.parse("2020-06-06"),
            aktivDato = LocalDate.parse("2020-06-06"),
            opprettetTidspunkt = ZonedDateTime.parse("2020-08-20T15:14:23.498+02:00"),
            ferdigstiltTidspunkt = null,
            endretAv = null,
        )

        return serialize(response)
    }

    private val stubMapping = WireMock.post(urlPathEqualTo(OPPGAVE_PATH))
        .withHeader("Authorization", WireMock.equalTo(bearertoken))
        .withHeader("X-Correlation-ID", WireMock.equalTo("correlationId"))
        .withHeader("Accept", WireMock.equalTo("application/json"))
        .withHeader("Content-Type", WireMock.equalTo("application/json"))
}
