package no.nav.su.se.bakover.client.oppgave

import arrow.core.left
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
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.ident.NavIdentBruker.Saksbehandler
import no.nav.su.se.bakover.common.infrastructure.config.ApplicationConfig
import no.nav.su.se.bakover.common.journal.JournalpostId
import no.nav.su.se.bakover.domain.klage.AvsluttetKlageinstansUtfall
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import no.nav.su.se.bakover.oppgave.domain.KunneIkkeOppretteOppgave
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
import java.util.UUID

internal class OppgaveHttpClientTest {

    private val saksbehandler = Saksbehandler("Z12345")
    private val aktørId = "333"
    private val journalpostId = JournalpostId("444")
    private val søknadId = UUID.randomUUID()
    private val saksnummer = Saksnummer(12345)

    @Test
    fun `oppretter oppgave for saksbehandler`() {
        startedWireMockServerWithCorrelationId {
            val expectedSaksbehandlingRequest = createRequestBody(tilordnetRessurs = saksbehandler)
            val response = createResponse()

            stubFor(
                stubMapping.withRequestBody(equalToJson(expectedSaksbehandlingRequest))
                    .willReturn(aResponse().withBody(response).withStatus(201)),
            )

            val oathMock = mock<AzureAd> { on { onBehalfOfToken(any(), any()) } doReturn "token" }

            val client = OppgaveHttpClient(
                connectionConfig = ApplicationConfig.ClientsConfig.OppgaveConfig(
                    clientId = "oppgaveClientId",
                    url = baseUrl(),
                ),
                exchange = oathMock,
                clock = fixedClock,
            )

            val actual = client.opprettOppgave(
                OppgaveConfig.Søknad(
                    sakstype = Sakstype.UFØRE,
                    journalpostId = journalpostId,
                    søknadId = søknadId,
                    fnr = fnr,
                    tilordnetRessurs = saksbehandler,
                    clock = fixedClock,
                ),
            ).getOrFail()

            val expected = nyOppgaveHttpKallResponse(
                oppgaveId = oppgaveId,
                oppgavetype = Oppgavetype.BEHANDLE_SAK,
                request = expectedSaksbehandlingRequest,
                response = response,
                beskrivelse = "--- 01.01.2021 02:02 - Opprettet av Supplerende Stønad ---\nSøknadId : $søknadId",
            )

            actual.oppgaveId shouldBe expected.oppgaveId
            actual.oppgavetype shouldBe expected.oppgavetype
            actual.beskrivelse shouldBe expected.beskrivelse
            actual.response shouldBe expected.response
            JSONAssert.assertEquals(actual.request, expected.request, true)

            verify(oathMock).onBehalfOfToken(
                originalToken = argThat { it shouldBe "Bearer token" },
                otherAppId = argThat { it shouldBe "oppgaveClientId" },
            )
            verifyNoMoreInteractions(oathMock)
        }
    }

    @Test
    fun `opprett oppgave med systembruker`() {
        startedWireMockServerWithCorrelationId {
            val expectedSaksbehandlingRequest = createRequestBody()
            val response = createResponse()

            stubFor(
                stubMapping.withRequestBody(equalToJson(expectedSaksbehandlingRequest))
                    .willReturn(aResponse().withBody(response).withStatus(201)),
            )

            val oathMock = mock<AzureAd> { on { getSystemToken(any()) } doReturn "token" }

            val client = OppgaveHttpClient(
                connectionConfig = ApplicationConfig.ClientsConfig.OppgaveConfig(
                    clientId = "oppgaveClientId",
                    url = baseUrl(),
                ),
                exchange = oathMock,
                clock = fixedClock,
            )
            val expected = nyOppgaveHttpKallResponse(
                oppgaveId = oppgaveId,
                oppgavetype = Oppgavetype.BEHANDLE_SAK,
                request = expectedSaksbehandlingRequest,
                response = response,
                beskrivelse = "--- 01.01.2021 02:02 - Opprettet av Supplerende Stønad ---\nSøknadId : $søknadId",
            )

            val actual = client.opprettOppgaveMedSystembruker(
                OppgaveConfig.Søknad(
                    sakstype = Sakstype.UFØRE,
                    journalpostId = journalpostId,
                    søknadId = søknadId,
                    fnr = fnr,
                    tilordnetRessurs = null,
                    clock = fixedClock,
                ),
            ).getOrFail()

            actual.oppgaveId shouldBe expected.oppgaveId
            actual.oppgavetype shouldBe expected.oppgavetype
            actual.beskrivelse shouldBe expected.beskrivelse
            actual.response shouldBe expected.response
            JSONAssert.assertEquals(actual.request, expected.request, true)

            verify(oathMock).getSystemToken(any())
            verifyNoMoreInteractions(oathMock)
        }
    }

    @Test
    fun `opprett attestering oppgave`() {
        startedWireMockServerWithCorrelationId {
            val expectedAttesteringRequest = createRequestBody(jpostId = null, oppgavetype = "ATT")
            val response = createResponse(oppgavetype = "ATT")

            stubFor(
                stubMapping.withRequestBody(equalToJson(expectedAttesteringRequest))
                    .willReturn(aResponse().withBody(response).withStatus(201)),
            )

            val oathMock = mock<AzureAd> { on { onBehalfOfToken(any(), any()) } doReturn "token" }

            val client = OppgaveHttpClient(
                connectionConfig = ApplicationConfig.ClientsConfig.OppgaveConfig(
                    clientId = "oppgaveClientId",
                    url = baseUrl(),
                ),
                exchange = oathMock,
                clock = fixedClock,
            )
            val actual = client.opprettOppgave(
                OppgaveConfig.AttesterSøknadsbehandling(
                    søknadId = søknadId,
                    fnr = fnr,
                    clock = fixedClock,
                    tilordnetRessurs = null,
                ),
            ).getOrFail()

            val expected = nyOppgaveHttpKallResponse(
                oppgaveId = oppgaveId,
                oppgavetype = Oppgavetype.ATTESTERING,
                request = expectedAttesteringRequest,
                response = response,
                beskrivelse = "--- 01.01.2021 02:02 - Opprettet av Supplerende Stønad ---\nSøknadId : $søknadId",
            )

            actual.oppgaveId shouldBe expected.oppgaveId
            actual.oppgavetype shouldBe expected.oppgavetype
            actual.beskrivelse shouldBe expected.beskrivelse
            actual.response shouldBe expected.response
            JSONAssert.assertEquals(actual.request, expected.request, true)
        }
    }

    @Test
    fun `returns KunneIkkeOppretteOppgave`() {
        startedWireMockServerWithCorrelationId {
            stubFor(stubMapping.willReturn(forbidden()))

            val oathMock = mock<AzureAd> { on { onBehalfOfToken(any(), any()) } doReturn "token" }

            val client = OppgaveHttpClient(
                connectionConfig = ApplicationConfig.ClientsConfig.OppgaveConfig(
                    clientId = "oppgaveClientId",
                    url = baseUrl(),
                ),
                exchange = oathMock,
                clock = fixedClock,
            )
            client.opprettOppgave(
                OppgaveConfig.Søknad(
                    sakstype = Sakstype.UFØRE,
                    journalpostId = journalpostId,
                    søknadId = søknadId,
                    fnr = fnr,
                    clock = fixedClock,
                    tilordnetRessurs = null,
                ),
            ) shouldBe KunneIkkeOppretteOppgave.left()
        }
    }

    @Test
    fun `oppretter en saksbehandling for en revurdering`() {
        startedWireMockServerWithCorrelationId {
            val expectedSaksbehandlingRequest = createRequestBody(
                jpostId = null,
                saksreferanse = saksnummer.toString(),
                beskrivelse = """--- 01.01.2021 02:02 - Opprettet av Supplerende Stønad ---\nSaksnummer : $saksnummer""",
                behandlingstype = "ae0028",
            )
            val response = createResponse()

            stubFor(
                stubMapping.withRequestBody(equalToJson(expectedSaksbehandlingRequest))
                    .willReturn(aResponse().withBody(response).withStatus(201)),
            )

            val oathMock = mock<AzureAd> { on { onBehalfOfToken(any(), any()) } doReturn "token" }

            val client = OppgaveHttpClient(
                connectionConfig = ApplicationConfig.ClientsConfig.OppgaveConfig(
                    clientId = "oppgaveClientId",
                    url = baseUrl(),
                ),
                exchange = oathMock,
                clock = fixedClock,
            )

            val actual = client.opprettOppgave(
                OppgaveConfig.Revurderingsbehandling(
                    saksnummer = saksnummer,
                    fnr = fnr,
                    tilordnetRessurs = null,
                    clock = fixedClock,
                ),
            ).getOrFail()

            val expected = nyOppgaveHttpKallResponse(
                oppgaveId = oppgaveId,
                oppgavetype = Oppgavetype.BEHANDLE_SAK,
                request = expectedSaksbehandlingRequest,
                response = response,
                beskrivelse = "--- 01.01.2021 02:02 - Opprettet av Supplerende Stønad ---\nSaksnummer : $saksnummer",
            )

            actual.oppgaveId shouldBe expected.oppgaveId
            actual.oppgavetype shouldBe expected.oppgavetype
            actual.beskrivelse shouldBe expected.beskrivelse
            actual.response shouldBe expected.response
            JSONAssert.assertEquals(actual.request, expected.request, true)

            verify(oathMock).onBehalfOfToken(
                originalToken = argThat { it shouldBe "Bearer token" },
                otherAppId = argThat { it shouldBe "oppgaveClientId" },
            )
            verifyNoMoreInteractions(oathMock)
        }
    }

    @Test
    fun `oppretter en saksbehandling for en revurdering med systembruker`() {
        startedWireMockServerWithCorrelationId {
            val expectedSaksbehandlingRequest = createRequestBody(
                jpostId = null,
                saksreferanse = saksnummer.toString(),
                beskrivelse = """--- 01.01.2021 02:02 - Opprettet av Supplerende Stønad ---\nSaksnummer : $saksnummer""",
                behandlingstype = "ae0028",
            )
            val response = createResponse()

            stubFor(
                stubMapping.withRequestBody(equalToJson(expectedSaksbehandlingRequest))
                    .willReturn(aResponse().withBody(response).withStatus(201)),
            )

            val oathMock = mock<AzureAd> { on { getSystemToken(any()) } doReturn "token" }

            val client = OppgaveHttpClient(
                connectionConfig = ApplicationConfig.ClientsConfig.OppgaveConfig(
                    clientId = "oppgaveClientId",
                    url = baseUrl(),
                ),
                exchange = oathMock,
                clock = fixedClock,
            )

            val expected = nyOppgaveHttpKallResponse(
                oppgaveId = oppgaveId,
                oppgavetype = Oppgavetype.BEHANDLE_SAK,
                request = expectedSaksbehandlingRequest,
                response = response,
                beskrivelse = "--- 01.01.2021 02:02 - Opprettet av Supplerende Stønad ---\nSaksnummer : $saksnummer",
            )

            val actual = client.opprettOppgaveMedSystembruker(
                OppgaveConfig.Revurderingsbehandling(
                    saksnummer = saksnummer,
                    fnr = fnr,
                    tilordnetRessurs = null,
                    clock = fixedClock,
                ),
            ).getOrFail()

            actual.oppgaveId shouldBe expected.oppgaveId
            actual.oppgavetype shouldBe expected.oppgavetype
            actual.beskrivelse shouldBe expected.beskrivelse
            actual.response shouldBe expected.response
            JSONAssert.assertEquals(actual.request, expected.request, true)

            verify(oathMock).getSystemToken(any())
            verifyNoMoreInteractions(oathMock)
        }
    }

    @Test
    fun `opprett attestering oppgave for revurdering`() {
        startedWireMockServerWithCorrelationId {
            val expectedAttesteringRequest = createRequestBody(
                jpostId = null,
                saksreferanse = saksnummer.toString(),
                beskrivelse = """--- 01.01.2021 02:02 - Opprettet av Supplerende Stønad ---\nSaksnummer : $saksnummer""",
                oppgavetype = "ATT",
                behandlingstype = "ae0028",
            )

            val response = createResponse(
                beskrivelse = """--- 01.01.2021 02:02 - Opprettet av Supplerende Stønad ---\nSaksnummer : $saksnummer""",
                oppgavetype = "ATT",
                behandlingstype = "ae0028",
            )

            stubFor(
                stubMapping.withRequestBody(equalToJson(expectedAttesteringRequest))
                    .willReturn(aResponse().withBody(response).withStatus(201)),
            )

            val oathMock = mock<AzureAd> { on { onBehalfOfToken(any(), any()) } doReturn "token" }

            val client = OppgaveHttpClient(
                connectionConfig = ApplicationConfig.ClientsConfig.OppgaveConfig(
                    clientId = "oppgaveClientId",
                    url = baseUrl(),
                ),
                exchange = oathMock,
                clock = fixedClock,
            )
            val actual = client.opprettOppgave(
                OppgaveConfig.AttesterRevurdering(
                    saksnummer = saksnummer,
                    fnr = fnr,
                    tilordnetRessurs = null,
                    clock = fixedClock,
                ),
            ).getOrFail()

            val expected = nyOppgaveHttpKallResponse(
                oppgaveId = oppgaveId,
                oppgavetype = Oppgavetype.ATTESTERING,
                request = expectedAttesteringRequest,
                response = response,
                beskrivelse = "--- 01.01.2021 02:02 - Opprettet av Supplerende Stønad ---\nSaksnummer : $saksnummer",
            )

            actual.oppgaveId shouldBe expected.oppgaveId
            actual.oppgavetype shouldBe expected.oppgavetype
            actual.beskrivelse shouldBe expected.beskrivelse
            actual.response shouldBe expected.response
            JSONAssert.assertEquals(actual.request, expected.request, true)
        }
    }

    @Test
    fun `opprett oppgave feiler med connection reset by peer`() {
        startedWireMockServerWithCorrelationId {
            stubFor(
                WireMock.post(urlPathEqualTo(OPPGAVE_PATH))
                    .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)),
            )

            val oathMock = mock<AzureAd> { on { onBehalfOfToken(any(), any()) } doReturn "token" }

            val client = OppgaveHttpClient(
                connectionConfig = ApplicationConfig.ClientsConfig.OppgaveConfig(
                    clientId = "oppgaveClientId",
                    url = baseUrl(),
                ),
                exchange = oathMock,
                clock = fixedClock,
            )
            client.opprettOppgave(
                OppgaveConfig.AttesterRevurdering(
                    saksnummer = saksnummer,
                    fnr = fnr,
                    tilordnetRessurs = null,
                    clock = fixedClock,
                ),
            ) shouldBe KunneIkkeOppretteOppgave.left()
        }
    }

    @Test
    fun `oppretter STADFESTELSE-oppgave for klageinstanshendelse`() {
        startedWireMockServerWithCorrelationId {
            val expectedAttesteringRequest = createRequestBody(
                jpostId = null,
                saksreferanse = saksnummer.toString(),
                beskrivelse = """--- 01.01.2021 02:02 - Opprettet av Supplerende Stønad ---\nSaksnummer : $saksnummer\nUtfall: Stadfestelse\nHendelsestype: enHendelse\nRelevante JournalpostIDer: 123, 456\nAvsluttet tidspunkt: 01.01.2021 02:02\n\nDenne oppgaven er kun til opplysning og må lukkes manuelt.""",
                oppgavetype = "VUR_KONS_YTE",
                behandlingstype = "ae0058",
            )
            val response = createResponse(oppgavetype = "ATT")

            stubFor(
                stubMapping.withRequestBody(equalToJson(expectedAttesteringRequest))
                    .willReturn(aResponse().withBody(response).withStatus(201)),
            )

            val oathMock = mock<AzureAd> { on { onBehalfOfToken(any(), any()) } doReturn "token" }

            val client = OppgaveHttpClient(
                connectionConfig = ApplicationConfig.ClientsConfig.OppgaveConfig(
                    clientId = "oppgaveClientId",
                    url = baseUrl(),
                ),
                exchange = oathMock,
                clock = fixedClock,
            )
            val actual = client.opprettOppgave(
                OppgaveConfig.Klage.Klageinstanshendelse.AvsluttetKlageinstansUtfall.Informasjon(
                    saksnummer = saksnummer,
                    fnr = fnr,
                    tilordnetRessurs = null,
                    clock = fixedClock,
                    utfall = AvsluttetKlageinstansUtfall.TilInformasjon.Stadfestelse,
                    avsluttetTidspunkt = fixedTidspunkt,
                    journalpostIDer = listOf(JournalpostId("123"), JournalpostId("456")),
                    hendelsestype = "enHendelse",
                ),
            ).getOrFail()

            val expected = nyOppgaveHttpKallResponse(
                oppgaveId = oppgaveId,
                oppgavetype = Oppgavetype.ATTESTERING,
                request = expectedAttesteringRequest,
                response = response,
                beskrivelse = "--- 01.01.2021 02:02 - Opprettet av Supplerende Stønad ---\nSaksnummer : $saksnummer\nUtfall: Stadfestelse\nHendelsestype: enHendelse\nRelevante JournalpostIDer: 123, 456\nAvsluttet tidspunkt: 01.01.2021 02:02\n\nDenne oppgaven er kun til opplysning og må lukkes manuelt.",
            )

            actual.oppgaveId shouldBe expected.oppgaveId
            actual.oppgavetype shouldBe expected.oppgavetype
            actual.beskrivelse shouldBe expected.beskrivelse
            actual.response shouldBe expected.response
            JSONAssert.assertEquals(actual.request, expected.request, true)
        }
    }

    @Test
    fun `oppretter MEDHOLD-oppgave for klageinstanshendelse`() {
        startedWireMockServerWithCorrelationId {
            val expectedAttesteringRequest = createRequestBody(
                jpostId = null,
                saksreferanse = saksnummer.toString(),
                beskrivelse = """--- 01.01.2021 02:02 - Opprettet av Supplerende Stønad ---\nSaksnummer : $saksnummer\nUtfall: Retur\nHendelsestype: enHendelse\nRelevante JournalpostIDer: 123, 456\nAvsluttet tidspunkt: 01.01.2021 02:02\n\nKlagen krever ytterligere saksbehandling. Lukking av oppgaven håndteres automatisk.""",
                behandlingstype = "ae0058",
            )
            val response = createResponse(oppgavetype = "ATT")

            stubFor(
                stubMapping.withRequestBody(equalToJson(expectedAttesteringRequest))
                    .willReturn(aResponse().withBody(response).withStatus(201)),
            )

            val oathMock = mock<AzureAd> { on { onBehalfOfToken(any(), any()) } doReturn "token" }
            val client = OppgaveHttpClient(
                connectionConfig = ApplicationConfig.ClientsConfig.OppgaveConfig(
                    clientId = "oppgaveClientId",
                    url = baseUrl(),
                ),
                exchange = oathMock,
                clock = fixedClock,
            )

            val actual = client.opprettOppgave(
                OppgaveConfig.Klage.Klageinstanshendelse.AvsluttetKlageinstansUtfall.Handling(
                    saksnummer = saksnummer,
                    fnr = fnr,
                    tilordnetRessurs = null,
                    clock = fixedClock,
                    utfall = AvsluttetKlageinstansUtfall.Retur,
                    avsluttetTidspunkt = fixedTidspunkt,
                    journalpostIDer = listOf(JournalpostId("123"), JournalpostId("456")),
                    hendelsestype = "enHendelse",
                ),
            ).getOrFail()

            val expected = nyOppgaveHttpKallResponse(
                oppgaveId = oppgaveId,
                oppgavetype = Oppgavetype.ATTESTERING,
                request = expectedAttesteringRequest,
                response = response,
                beskrivelse = "--- 01.01.2021 02:02 - Opprettet av Supplerende Stønad ---\nSaksnummer : $saksnummer\nUtfall: Retur\nHendelsestype: enHendelse\nRelevante JournalpostIDer: 123, 456\nAvsluttet tidspunkt: 01.01.2021 02:02\n\nKlagen krever ytterligere saksbehandling. Lukking av oppgaven håndteres automatisk.",
            )

            actual.oppgaveId shouldBe expected.oppgaveId
            actual.oppgavetype shouldBe expected.oppgavetype
            actual.beskrivelse shouldBe expected.beskrivelse
            actual.response shouldBe expected.response
            JSONAssert.assertEquals(actual.request, expected.request, true)
        }
    }

    @Test
    fun `oppretter RETUR-oppgave for klageinstanshendelse`() {
        startedWireMockServerWithCorrelationId {
            val expectedAttesteringRequest = createRequestBody(
                jpostId = null,
                saksreferanse = saksnummer.toString(),
                beskrivelse = """--- 01.01.2021 02:02 - Opprettet av Supplerende Stønad ---\nSaksnummer : $saksnummer\nUtfall: Retur\nHendelsestype: enHendelse\nRelevante JournalpostIDer: 123, 456\nAvsluttet tidspunkt: 01.01.2021 02:02\n\nKlagen krever ytterligere saksbehandling. Lukking av oppgaven håndteres automatisk.""",
                behandlingstype = "ae0058",
            )

            val response = createResponse(oppgavetype = "ATT")

            stubFor(
                stubMapping.withRequestBody(equalToJson(expectedAttesteringRequest))
                    .willReturn(aResponse().withBody(response).withStatus(201)),
            )

            val oathMock = mock<AzureAd> { on { onBehalfOfToken(any(), any()) } doReturn "token" }

            val client = OppgaveHttpClient(
                connectionConfig = ApplicationConfig.ClientsConfig.OppgaveConfig(
                    clientId = "oppgaveClientId",
                    url = baseUrl(),
                ),
                exchange = oathMock,
                clock = fixedClock,
            )
            val actual = client.opprettOppgave(
                OppgaveConfig.Klage.Klageinstanshendelse.AvsluttetKlageinstansUtfall.Handling(
                    saksnummer = saksnummer,
                    fnr = fnr,
                    tilordnetRessurs = null,
                    clock = fixedClock,
                    utfall = AvsluttetKlageinstansUtfall.Retur,
                    avsluttetTidspunkt = fixedTidspunkt,
                    journalpostIDer = listOf(JournalpostId("123"), JournalpostId("456")),
                    hendelsestype = "enHendelse",
                ),
            ).getOrFail()

            val expected = nyOppgaveHttpKallResponse(
                oppgaveId = oppgaveId,
                oppgavetype = Oppgavetype.ATTESTERING,
                request = expectedAttesteringRequest,
                response = response,
                beskrivelse = "--- 01.01.2021 02:02 - Opprettet av Supplerende Stønad ---\nSaksnummer : $saksnummer\nUtfall: Retur\nHendelsestype: enHendelse\nRelevante JournalpostIDer: 123, 456\nAvsluttet tidspunkt: 01.01.2021 02:02\n\nKlagen krever ytterligere saksbehandling. Lukking av oppgaven håndteres automatisk.",
            )

            actual.oppgaveId shouldBe expected.oppgaveId
            actual.oppgavetype shouldBe expected.oppgavetype
            actual.beskrivelse shouldBe expected.beskrivelse
            actual.response shouldBe expected.response
            JSONAssert.assertEquals(actual.request, expected.request, true)
        }
    }

    private fun createRequestBody(
        jpostId: JournalpostId? = journalpostId,
        saksreferanse: String = "$søknadId",
        tilordnetRessurs: Saksbehandler? = null,
        beskrivelse: String = """--- 01.01.2021 02:02 - Opprettet av Supplerende Stønad ---\nSøknadId : $søknadId""",
        oppgavetype: String = "BEH_SAK",
        behandlingstype: String = "ae0034",
    ): String {
        //language=json
        return """
                {
                    "journalpostId": ${jpostId?.let { "\"$it\"" }},
                    "saksreferanse": "$saksreferanse",
                    "personident": "$fnr",
                    "tema": "SUP",
                    "beskrivelse": "$beskrivelse",
                    "oppgavetype": "$oppgavetype",
                    "behandlingstema": "ab0431",
                    "behandlingstype": "$behandlingstype",
                    "aktivDato": "2021-01-01",
                    "fristFerdigstillelse": "2021-01-31",
                    "prioritet": "NORM",
                    "tilordnetRessurs": ${tilordnetRessurs?.let { "\"$it\"" }},
                    "tildeltEnhetsnr":  ${tilordnetRessurs?.let { "\"4815\"" }}
                }
        """.trimIndent()
    }

    private fun createResponse(
        tilordnetResurs: Saksbehandler? = null,
        beskrivelse: String = """--- 01.01.2021 02:02 - Opprettet av Supplerende Stønad ---\nSøknadId : $søknadId """,
        oppgavetype: String = "BEH_SAK",
        behandlingstype: String = "ae0034",
    ): String {
        //language=json
        return """
                {
                  "id": 123,
                  "tildeltEnhetsnr": "4815",
                  "journalpostId": "$journalpostId",
                  "saksreferanse": "$søknadId",
                  "aktoerId": "$aktørId",
                  "tilordnetRessurs": ${tilordnetResurs?.let { "\"$it\"" }},
                  "tema": "SUP",
                  "beskrivelse": "$beskrivelse",
                  "behandlingstema": "ab0431",
                  "oppgavetype": "$oppgavetype",
                  "behandlingstype":  "$behandlingstype",
                  "versjon": 1,
                  "fristFerdigstillelse": "2020-06-06",
                  "aktivDato": "2020-06-06",
                  "opprettetTidspunkt": "2020-08-20T15:14:23.498+02:00",
                  "opprettetAv": "srvsupstonad",
                  "prioritet": "NORM",
                  "status": "OPPRETTET",
                  "metadata": {}
                }
        """.trimIndent()
    }

    private val stubMapping = WireMock.post(urlPathEqualTo(OPPGAVE_PATH))
        .withHeader("Authorization", WireMock.equalTo("Bearer token"))
        .withHeader("X-Correlation-ID", WireMock.equalTo("correlationId"))
        .withHeader("Accept", WireMock.equalTo("application/json"))
        .withHeader("Content-Type", WireMock.equalTo("application/json"))
}
