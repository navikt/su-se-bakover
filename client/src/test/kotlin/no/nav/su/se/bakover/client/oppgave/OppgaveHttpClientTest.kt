package no.nav.su.se.bakover.client.oppgave

import arrow.core.left
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.equalToJson
import com.github.tomakehurst.wiremock.client.WireMock.forbidden
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.http.Fault
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.client.AccessToken
import no.nav.su.se.bakover.client.WiremockBase
import no.nav.su.se.bakover.client.WiremockBase.Companion.wireMockServer
import no.nav.su.se.bakover.client.argThat
import no.nav.su.se.bakover.client.sts.TokenOppslag
import no.nav.su.se.bakover.common.auth.AzureAd
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.ident.NavIdentBruker.Saksbehandler
import no.nav.su.se.bakover.common.infrastructure.config.ApplicationConfig
import no.nav.su.se.bakover.common.journal.JournalpostId
import no.nav.su.se.bakover.common.person.AktørId
import no.nav.su.se.bakover.domain.klage.KlageinstansUtfall
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import no.nav.su.se.bakover.domain.oppgave.OppgaveFeil
import no.nav.su.se.bakover.oppgave.domain.Oppgavetype
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.oppgave.nyOppgaveHttpKallResponse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.skyscreamer.jsonassert.JSONAssert
import org.slf4j.MDC
import java.util.UUID

internal class OppgaveHttpClientTest : WiremockBase {

    private val saksbehandler = Saksbehandler("Z12345")
    private val aktørId = "333"
    private val journalpostId = JournalpostId("444")
    private val søknadId = UUID.randomUUID()
    private val saksnummer = Saksnummer(12345)

    @Test
    fun `opprett sakbehandling oppgave ny periode`() {
        //language=JSON
        val expectedSaksbehandlingRequest =
            """
                {
                    "journalpostId": "$journalpostId",
                    "saksreferanse": "$søknadId",
                    "aktoerId": "$aktørId",
                    "tema": "SUP",
                    "beskrivelse": "--- 01.01.2021 02:02 - Opprettet av Supplerende Stønad ---\nSøknadId : $søknadId",
                    "oppgavetype": "BEH_SAK",
                    "behandlingstema": "ab0431",
                    "behandlingstype": "ae0034",
                    "aktivDato": "2021-01-01",
                    "fristFerdigstillelse": "2021-01-31",
                    "prioritet": "NORM",
                    "tilordnetRessurs": null
                }
            """.trimMargin()

        //language=json
        val response = """
                                    {
                                                      "id": 111,
                                                      "tildeltEnhetsnr": "4811",
                                                      "journalpostId": "$journalpostId",
                                                      "saksreferanse": "$søknadId",
                                                      "aktoerId": "$aktørId",
                                                      "tema": "SUP",
                                                      "beskrivelse": "--- 01.01.2021 02:02 - Opprettet av Supplerende Stønad ---\nSøknadId : $søknadId ",
                                                      "behandlingstema": "ab0431",
                                                      "oppgavetype": "BEH_SAK",
                                                      "behandlingstype": "ae0034",
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

        wireMockServer.stubFor(
            stubMapping.withRequestBody(equalToJson(expectedSaksbehandlingRequest))
                .willReturn(aResponse().withBody(response).withStatus(201)),
        )

        val oathMock = mock<AzureAd> {
            on { onBehalfOfToken(any(), any()) } doReturn "token"
        }

        val tokenoppslagMock = mock<TokenOppslag> {
            on { token() } doReturn AccessToken("token")
        }
        val client = OppgaveHttpClient(
            connectionConfig = ApplicationConfig.ClientsConfig.OppgaveConfig(
                clientId = "oppgaveClientId",
                url = wireMockServer.baseUrl(),
            ),
            exchange = oathMock,
            tokenoppslagForSystembruker = tokenoppslagMock,
            clock = fixedClock,
        )

        val firstActual = client.opprettOppgave(
            OppgaveConfig.Søknad(
                sakstype = Sakstype.UFØRE,
                journalpostId = journalpostId,
                søknadId = søknadId,
                aktørId = AktørId(aktørId),
                tilordnetRessurs = null,
                clock = fixedClock,
            ),
        ).getOrFail()

        val exoected = nyOppgaveHttpKallResponse(
            oppgaveId = OppgaveId("111"),
            oppgavetype = Oppgavetype.BEHANDLE_SAK,
            requestBody = expectedSaksbehandlingRequest,
            response = response,
            beskrivelse = "--- 01.01.2021 02:02 - Opprettet av Supplerende Stønad ---\nSøknadId : $søknadId",
        )

        firstActual.oppgaveId shouldBe exoected.oppgaveId
        firstActual.oppgavetype shouldBe exoected.oppgavetype
        firstActual.beskrivelse shouldBe exoected.beskrivelse
        firstActual.response shouldBe exoected.response
        JSONAssert.assertEquals(firstActual.requestBody, exoected.requestBody, true)

        verify(oathMock).onBehalfOfToken(
            originalToken = argThat { it shouldBe "Bearer token" },
            otherAppId = argThat { it shouldBe "oppgaveClientId" },
        )
        verifyNoMoreInteractions(oathMock, tokenoppslagMock)

        val secondActual = client.opprettOppgaveMedSystembruker(
            OppgaveConfig.Søknad(
                sakstype = Sakstype.UFØRE,
                journalpostId = journalpostId,
                søknadId = søknadId,
                aktørId = AktørId(aktørId),
                tilordnetRessurs = null,
                clock = fixedClock,
            ),
        ).getOrFail()

        secondActual.oppgaveId shouldBe exoected.oppgaveId
        secondActual.oppgavetype shouldBe exoected.oppgavetype
        secondActual.beskrivelse shouldBe exoected.beskrivelse
        secondActual.response shouldBe exoected.response
        JSONAssert.assertEquals(secondActual.requestBody, exoected.requestBody, true)

        verify(tokenoppslagMock).token()
        verifyNoMoreInteractions(oathMock, tokenoppslagMock)
    }

    @Test
    fun `opprett sakbehandling oppgave med tilordnet ressurs`() {
        //language=JSON
        val expectedSaksbehandlingRequest =
            """
                {
                    "journalpostId": "$journalpostId",
                    "saksreferanse": "$søknadId",
                    "aktoerId": "$aktørId",
                    "tema": "SUP",
                    "beskrivelse": "--- 01.01.2021 02:02 - Opprettet av Supplerende Stønad ---\nSøknadId : $søknadId",
                    "oppgavetype": "BEH_SAK",
                    "behandlingstema": "ab0431",
                    "behandlingstype": "ae0034",
                    "aktivDato": "2021-01-01",
                    "fristFerdigstillelse": "2021-01-31",
                    "prioritet": "NORM",
                    "tilordnetRessurs": "$saksbehandler"
                }
            """.trimMargin()

        val response =
            //language=JSON
            """
                                    {
                                                      "id": 111,
                                                      "tildeltEnhetsnr": "4811",
                                                      "journalpostId": "$journalpostId",
                                                      "saksreferanse": "$søknadId",
                                                      "aktoerId": "$aktørId",
                                                      "tilordnetRessurs": "$saksbehandler",
                                                      "tema": "SUP",
                                                      "behandlingstema": "ab0431",
                                                      "beskrivelse": "--- 01.01.2021 02:02 - Opprettet av Supplerende Stønad ---\nSøknadId : $søknadId",
                                                      "oppgavetype": "BEH_SAK",
                                                      "behandlingstype": "ae0034",
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

        wireMockServer.stubFor(
            stubMapping.withRequestBody(equalToJson(expectedSaksbehandlingRequest))
                .willReturn(aResponse().withBody(response).withStatus(201)),
        )

        val oathMock = mock<AzureAd> {
            on { onBehalfOfToken(any(), any()) } doReturn "token"
        }

        val client = OppgaveHttpClient(
            connectionConfig = ApplicationConfig.ClientsConfig.OppgaveConfig(
                clientId = "oppgaveClientId",
                url = wireMockServer.baseUrl(),
            ),
            exchange = oathMock,
            tokenoppslagForSystembruker = mock(),
            clock = fixedClock,
        )
        val actual = client.opprettOppgave(
            OppgaveConfig.Søknad(
                sakstype = Sakstype.UFØRE,
                journalpostId = journalpostId,
                søknadId = søknadId,
                aktørId = AktørId(aktørId),
                tilordnetRessurs = saksbehandler,
                clock = fixedClock,
            ),
        ).getOrFail()

        val expected = nyOppgaveHttpKallResponse(
            oppgaveId = OppgaveId("111"),
            oppgavetype = Oppgavetype.BEHANDLE_SAK,
            requestBody = expectedSaksbehandlingRequest,
            response = response,
            beskrivelse = "--- 01.01.2021 02:02 - Opprettet av Supplerende Stønad ---\nSøknadId : $søknadId",
        )

        actual.oppgaveId shouldBe expected.oppgaveId
        actual.oppgavetype shouldBe expected.oppgavetype
        actual.beskrivelse shouldBe expected.beskrivelse
        actual.response shouldBe expected.response
        JSONAssert.assertEquals(actual.requestBody, expected.requestBody, true)
    }

    @Test
    fun `opprett attestering oppgave`() {
        //language=JSON
        val expectedAttesteringRequest =
            """
                {
                    "journalpostId": null,
                    "saksreferanse": "$søknadId",
                    "aktoerId": "$aktørId",
                    "tema": "SUP",
                    "beskrivelse": "--- 01.01.2021 02:02 - Opprettet av Supplerende Stønad ---\nSøknadId : $søknadId",
                    "oppgavetype": "ATT",
                    "behandlingstema": "ab0431",
                    "behandlingstype": "ae0034",
                    "aktivDato": "2021-01-01",
                    "fristFerdigstillelse": "2021-01-31",
                    "prioritet": "NORM",
                    "tilordnetRessurs": null
                }
            """.trimMargin()

        //language=json
        val response = """
                                    {
                                                      "id": 111,
                                                      "tildeltEnhetsnr": "4811",
                                                      "saksreferanse": "$søknadId",
                                                      "aktoerId": "$aktørId",
                                                      "tema": "SUP",
                                                      "beskrivelse": "--- 01.01.2021 02:02 - Opprettet av Supplerende Stønad ---\nSøknadId : $søknadId ",
                                                      "behandlingstema": "ab0431",
                                                      "oppgavetype": "ATT",
                                                      "behandlingstype": "ae0034",
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

        wireMockServer.stubFor(
            stubMapping.withRequestBody(equalToJson(expectedAttesteringRequest))
                .willReturn(aResponse().withBody(response).withStatus(201)),
        )

        val oathMock = mock<AzureAd> {
            on { onBehalfOfToken(any(), any()) } doReturn "token"
        }
        val client = OppgaveHttpClient(
            connectionConfig = ApplicationConfig.ClientsConfig.OppgaveConfig(
                clientId = "oppgaveClientId",
                url = wireMockServer.baseUrl(),
            ),
            exchange = oathMock,
            tokenoppslagForSystembruker = mock(),
            clock = fixedClock,
        )
        val actual = client.opprettOppgave(
            OppgaveConfig.AttesterSøknadsbehandling(
                søknadId = søknadId,
                aktørId = AktørId(aktørId),
                clock = fixedClock,
                tilordnetRessurs = null,
            ),
        ).getOrFail()

        val exoected = nyOppgaveHttpKallResponse(
            oppgaveId = OppgaveId("111"),
            oppgavetype = Oppgavetype.ATTESTERING,
            requestBody = expectedAttesteringRequest,
            response = response,
            beskrivelse = "--- 01.01.2021 02:02 - Opprettet av Supplerende Stønad ---\nSøknadId : $søknadId",
        )

        actual.oppgaveId shouldBe exoected.oppgaveId
        actual.oppgavetype shouldBe exoected.oppgavetype
        actual.beskrivelse shouldBe exoected.beskrivelse
        actual.response shouldBe exoected.response
        JSONAssert.assertEquals(actual.requestBody, exoected.requestBody, true)
    }

    @Test
    fun `returns KunneIkkeOppretteOppgave`() {
        wireMockServer.stubFor(stubMapping.willReturn(forbidden()))

        val oathMock = mock<AzureAd> {
            on { onBehalfOfToken(any(), any()) } doReturn "token"
        }
        val client = OppgaveHttpClient(
            connectionConfig = ApplicationConfig.ClientsConfig.OppgaveConfig(
                clientId = "oppgaveClientId",
                url = wireMockServer.baseUrl(),
            ),
            exchange = oathMock,
            tokenoppslagForSystembruker = mock(),
            clock = fixedClock,
        )
        client.opprettOppgave(
            OppgaveConfig.Søknad(
                sakstype = Sakstype.UFØRE,
                journalpostId = journalpostId,
                søknadId = søknadId,
                aktørId = AktørId(aktørId),
                clock = fixedClock,
                tilordnetRessurs = null,
            ),
        ) shouldBe OppgaveFeil.KunneIkkeOppretteOppgave.left()
    }

    @Test
    fun `oppretter en saksbehandling for en revurdering`() {
        //language=JSON
        val expectedSaksbehandlingRequest =
            """
                {
                    "journalpostId": null,
                    "saksreferanse": "$saksnummer",
                    "aktoerId": "$aktørId",
                    "tema": "SUP",
                    "beskrivelse": "--- 01.01.2021 02:02 - Opprettet av Supplerende Stønad ---\nSaksnummer : $saksnummer",
                    "oppgavetype": "BEH_SAK",
                    "behandlingstema": "ab0431",
                    "behandlingstype": "ae0028",
                    "aktivDato": "2021-01-01",
                    "fristFerdigstillelse": "2021-01-31",
                    "prioritet": "NORM",
                    "tilordnetRessurs": null
                }
            """.trimMargin()

        //language=json
        val response = """
                                    {
                                                      "id": 111,
                                                      "tildeltEnhetsnr": "4811",
                                                      "saksreferanse": "$søknadId",
                                                      "aktoerId": "$aktørId",
                                                      "tema": "SUP",
                                                      "beskrivelse": "--- 01.01.2021 02:02 - Opprettet av Supplerende Stønad ---\nSaksnummer : $saksnummer ",
                                                      "behandlingstema": "ab0431",
                                                      "oppgavetype": "BEH_SAK",
                                                      "behandlingstype": "ae0028",
                                                      "versjon": 1,
                                                      "fristFerdigstillelse": "2020-06-06",
                                                      "aktivDato": "2020-06-06",
                                                      "opprettetTidspunkt": "2020-08-20T15:14:23.498+02:00",
                                                      "opprettetAv": "srvsupstonad",
                                                      "prioritet": "NORM",
                                                      "status": "OPPRETTET",
                                                      "metadata": {}
                                                    }
                        """

        wireMockServer.stubFor(
            stubMapping.withRequestBody(equalToJson(expectedSaksbehandlingRequest))
                .willReturn(aResponse().withBody(response).withStatus(201)),
        )

        val oathMock = mock<AzureAd> {
            on { onBehalfOfToken(any(), any()) } doReturn "token"
        }

        val tokenoppslagMock = mock<TokenOppslag> {
            on { token() } doReturn AccessToken("token")
        }
        val client = OppgaveHttpClient(
            connectionConfig = ApplicationConfig.ClientsConfig.OppgaveConfig(
                clientId = "oppgaveClientId",
                url = wireMockServer.baseUrl(),
            ),
            exchange = oathMock,
            tokenoppslagForSystembruker = tokenoppslagMock,
            clock = fixedClock,
        )

        val actual = client.opprettOppgave(
            OppgaveConfig.Revurderingsbehandling(
                saksnummer = saksnummer,
                aktørId = AktørId(aktørId),
                tilordnetRessurs = null,
                clock = fixedClock,
            ),
        ).getOrFail()

        val exoected = nyOppgaveHttpKallResponse(
            oppgaveId = OppgaveId("111"),
            oppgavetype = Oppgavetype.BEHANDLE_SAK,
            requestBody = expectedSaksbehandlingRequest,
            response = response,
            beskrivelse = "--- 01.01.2021 02:02 - Opprettet av Supplerende Stønad ---\nSaksnummer : $saksnummer",
        )

        actual.oppgaveId shouldBe exoected.oppgaveId
        actual.oppgavetype shouldBe exoected.oppgavetype
        actual.beskrivelse shouldBe exoected.beskrivelse
        actual.response shouldBe exoected.response
        JSONAssert.assertEquals(actual.requestBody, exoected.requestBody, true)

        verify(oathMock).onBehalfOfToken(
            originalToken = argThat { it shouldBe "Bearer token" },
            otherAppId = argThat { it shouldBe "oppgaveClientId" },
        )
        verifyNoMoreInteractions(oathMock, tokenoppslagMock)

        val secondActual = client.opprettOppgaveMedSystembruker(
            OppgaveConfig.Revurderingsbehandling(
                saksnummer = saksnummer,
                aktørId = AktørId(aktørId),
                tilordnetRessurs = null,
                clock = fixedClock,
            ),
        ).getOrFail()

        secondActual.oppgaveId shouldBe exoected.oppgaveId
        secondActual.oppgavetype shouldBe exoected.oppgavetype
        secondActual.beskrivelse shouldBe exoected.beskrivelse
        secondActual.response shouldBe exoected.response
        JSONAssert.assertEquals(secondActual.requestBody, exoected.requestBody, true)

        verify(tokenoppslagMock).token()
        verifyNoMoreInteractions(oathMock, tokenoppslagMock)
    }

    @Test
    fun `opprett attestering oppgave for revurdering`() {
        //language=JSON
        val expectedAttesteringRequest =
            """
                {
                    "journalpostId": null,
                    "saksreferanse": "$saksnummer",
                    "aktoerId": "$aktørId",
                    "tema": "SUP",
                    "beskrivelse": "--- 01.01.2021 02:02 - Opprettet av Supplerende Stønad ---\nSaksnummer : $saksnummer",
                    "oppgavetype": "ATT",
                    "behandlingstema": "ab0431",
                    "behandlingstype": "ae0028",
                    "aktivDato": "2021-01-01",
                    "fristFerdigstillelse": "2021-01-31",
                    "prioritet": "NORM",
                    "tilordnetRessurs": null
                }
            """.trimMargin()

        val response =
            //language=JSON
            """
                                    {
                                                      "id": 111,
                                                      "tildeltEnhetsnr": "4811",
                                                      "saksreferanse": "$saksnummer",
                                                      "aktoerId": "$aktørId",
                                                      "tema": "SUP",
                                                      "beskrivelse": "--- 01.01.2021 02:02 - Opprettet av Supplerende Stønad ---\nSaksnummer : $saksnummer",
                                                      "behandlingstema": "ab0431",
                                                      "oppgavetype": "ATT",
                                                      "behandlingstype": "ae0028",
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

        wireMockServer.stubFor(
            stubMapping.withRequestBody(equalToJson(expectedAttesteringRequest))
                .willReturn(aResponse().withBody(response).withStatus(201)),
        )

        val oathMock = mock<AzureAd> {
            on { onBehalfOfToken(any(), any()) } doReturn "token"
        }
        val client = OppgaveHttpClient(
            connectionConfig = ApplicationConfig.ClientsConfig.OppgaveConfig(
                clientId = "oppgaveClientId",
                url = wireMockServer.baseUrl(),
            ),
            exchange = oathMock,
            tokenoppslagForSystembruker = mock(),
            clock = fixedClock,
        )
        val actual = client.opprettOppgave(
            OppgaveConfig.AttesterRevurdering(
                saksnummer = saksnummer,
                aktørId = AktørId(aktørId),
                tilordnetRessurs = null,
                clock = fixedClock,
            ),
        ).getOrFail()

        val exoected = nyOppgaveHttpKallResponse(
            oppgaveId = OppgaveId("111"),
            oppgavetype = Oppgavetype.ATTESTERING,
            requestBody = expectedAttesteringRequest,
            response = response,
            beskrivelse = "--- 01.01.2021 02:02 - Opprettet av Supplerende Stønad ---\nSaksnummer : $saksnummer",
        )

        actual.oppgaveId shouldBe exoected.oppgaveId
        actual.oppgavetype shouldBe exoected.oppgavetype
        actual.beskrivelse shouldBe exoected.beskrivelse
        actual.response shouldBe exoected.response
        JSONAssert.assertEquals(actual.requestBody, exoected.requestBody, true)
    }

    @Test
    fun `opprett oppgave feiler med connection reset by peer`() {
        wireMockServer.stubFor(
            WireMock.post(
                urlPathEqualTo(OPPGAVE_PATH),
            ).willReturn(
                aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER),
            ),
        )

        val oathMock = mock<AzureAd> {
            on { onBehalfOfToken(any(), any()) } doReturn "token"
        }
        val client = OppgaveHttpClient(
            connectionConfig = ApplicationConfig.ClientsConfig.OppgaveConfig(
                clientId = "oppgaveClientId",
                url = wireMockServer.baseUrl(),
            ),
            exchange = oathMock,
            tokenoppslagForSystembruker = mock(),
            clock = fixedClock,
        )
        client.opprettOppgave(
            OppgaveConfig.AttesterRevurdering(
                saksnummer = saksnummer,
                aktørId = AktørId(aktørId),
                tilordnetRessurs = null,
                clock = fixedClock,
            ),
        ) shouldBe OppgaveFeil.KunneIkkeOppretteOppgave.left()
    }

    @Test
    fun `oppretter STADFESTELSE-oppgave for klageinstanshendelse`() {
        //language=JSON
        val expectedAttesteringRequest =
            """
                {
                    "journalpostId": null,
                    "saksreferanse": "$saksnummer",
                    "aktoerId": "$aktørId",
                    "tema": "SUP",
                    "beskrivelse": "--- 01.01.2021 02:02 - Opprettet av Supplerende Stønad ---\nSaksnummer : $saksnummer\nUtfall: Stadfestelse\nRelevante JournalpostIDer: 123, 456\nKlageinstans sin behandling ble avsluttet den 01.01.2021 02:02\n\nDenne oppgaven er kun til opplysning og må lukkes manuelt.",
                    "oppgavetype": "VUR_KONS_YTE",
                    "behandlingstema": "ab0431",
                    "behandlingstype": "ae0058",
                    "aktivDato": "2021-01-01",
                    "fristFerdigstillelse": "2021-01-31",
                    "prioritet": "NORM",
                    "tilordnetRessurs": null
                }
            """.trimMargin()

        //language=json
        val response = """
                            {
                                 "id": 111,
                                  "tildeltEnhetsnr": "4811",
                                  "saksreferanse": "$søknadId",
                                  "aktoerId": "$aktørId",
                                  "tema": "SUP",
                                  "beskrivelse": "--- 01.01.2021 02:02 - Opprettet av Supplerende Stønad ---\nSøknadId : $søknadId ",
                                  "behandlingstema": "ab0431",
                                  "oppgavetype": "ATT",
                                  "behandlingstype": "ae0034",
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

        wireMockServer.stubFor(
            stubMapping.withRequestBody(equalToJson(expectedAttesteringRequest))
                .willReturn(aResponse().withBody(response).withStatus(201)),
        )

        val oathMock = mock<AzureAd> {
            on { onBehalfOfToken(any(), any()) } doReturn "token"
        }
        val client = OppgaveHttpClient(
            connectionConfig = ApplicationConfig.ClientsConfig.OppgaveConfig(
                clientId = "oppgaveClientId",
                url = wireMockServer.baseUrl(),
            ),
            exchange = oathMock,
            tokenoppslagForSystembruker = mock(),
            clock = fixedClock,
        )
        val actual = client.opprettOppgave(
            OppgaveConfig.Klage.Klageinstanshendelse.Informasjon(
                saksnummer = saksnummer,
                aktørId = AktørId(aktørId),
                tilordnetRessurs = null,
                clock = fixedClock,
                utfall = KlageinstansUtfall.STADFESTELSE,
                avsluttetTidspunkt = fixedTidspunkt,
                journalpostIDer = listOf(JournalpostId("123"), JournalpostId("456")),
            ),
        ).getOrFail()

        val expected = nyOppgaveHttpKallResponse(
            oppgaveId = OppgaveId("111"),
            oppgavetype = Oppgavetype.ATTESTERING,
            requestBody = expectedAttesteringRequest,
            response = response,
            beskrivelse = "--- 01.01.2021 02:02 - Opprettet av Supplerende Stønad ---\nSaksnummer : $saksnummer\nUtfall: Stadfestelse\nRelevante JournalpostIDer: 123, 456\nKlageinstans sin behandling ble avsluttet den 01.01.2021 02:02\n\nDenne oppgaven er kun til opplysning og må lukkes manuelt.",
        )

        actual.oppgaveId shouldBe expected.oppgaveId
        actual.oppgavetype shouldBe expected.oppgavetype
        actual.beskrivelse shouldBe expected.beskrivelse
        actual.response shouldBe expected.response
        JSONAssert.assertEquals(actual.requestBody, expected.requestBody, true)
    }

    @Test
    fun `oppretter MEDHOLD-oppgave for klageinstanshendelse`() {
        val expectedAttesteringRequest =
            """
                {
                  "journalpostId": null,
                  "saksreferanse": "$saksnummer",
                  "aktoerId": "$aktørId",
                  "tema": "SUP",
                  "beskrivelse": "--- 01.01.2021 02:02 - Opprettet av Supplerende Stønad ---\nSaksnummer : $saksnummer\nUtfall: Retur\nRelevante JournalpostIDer: 123, 456\nKlageinstans sin behandling ble avsluttet den 01.01.2021 02:02\n\nKlagen krever ytterligere saksbehandling. Lukking av oppgaven håndteres automatisk.",
                  "oppgavetype": "BEH_SAK",
                  "behandlingstema": "ab0431",
                  "behandlingstype": "ae0058",
                  "aktivDato": "2021-01-01",
                  "fristFerdigstillelse": "2021-01-31",
                  "prioritet": "NORM",
                  "tilordnetRessurs": null
                }
                """

        //language=json
        val body = """
                    {
                      "id": 111,
                       "tildeltEnhetsnr": "4811",
                       "saksreferanse": "$søknadId",
                       "aktoerId": "$aktørId",
                       "tema": "SUP",
                       "beskrivelse": "--- 01.01.2021 02:02 - Opprettet av Supplerende Stønad ---\nSøknadId : $søknadId ",
                       "behandlingstema": "ab0431",
                       "oppgavetype": "ATT",
                       "behandlingstype": "ae0034",
                       "versjon": 1,
                       "fristFerdigstillelse": "2020-06-06",
                       "aktivDato": "2020-06-06",
                       "opprettetTidspunkt": "2020-08-20T15:14:23.498+02:00",
                       "opprettetAv": "srvsupstonad",
                       "prioritet": "NORM",
                       "status": "OPPRETTET",
                       "metadata": {}
                    }
                    """

        wireMockServer.stubFor(
            stubMapping.withRequestBody(equalToJson(expectedAttesteringRequest))
                .willReturn(aResponse().withBody(body).withStatus(201)),
        )

        val oathMock = mock<AzureAd> {
            on { onBehalfOfToken(any(), any()) } doReturn "token"
        }
        val client = OppgaveHttpClient(
            connectionConfig = ApplicationConfig.ClientsConfig.OppgaveConfig(
                clientId = "oppgaveClientId",
                url = wireMockServer.baseUrl(),
            ),
            exchange = oathMock,
            tokenoppslagForSystembruker = mock(),
            clock = fixedClock,
        )
        val request = OppgaveConfig.Klage.Klageinstanshendelse.Handling(
            saksnummer = saksnummer,
            aktørId = AktørId(aktørId),
            tilordnetRessurs = null,
            clock = fixedClock,
            utfall = KlageinstansUtfall.RETUR,
            avsluttetTidspunkt = fixedTidspunkt,
            journalpostIDer = listOf(JournalpostId("123"), JournalpostId("456")),
        )
        val actual = client.opprettOppgave(request).getOrFail()

        val expected = nyOppgaveHttpKallResponse(
            oppgaveId = OppgaveId("111"),
            oppgavetype = Oppgavetype.ATTESTERING,
            requestBody = expectedAttesteringRequest,
            response = body,
            beskrivelse = "--- 01.01.2021 02:02 - Opprettet av Supplerende Stønad ---\nSaksnummer : $saksnummer\nUtfall: Retur\nRelevante JournalpostIDer: 123, 456\nKlageinstans sin behandling ble avsluttet den 01.01.2021 02:02\n\nKlagen krever ytterligere saksbehandling. Lukking av oppgaven håndteres automatisk.",
        )

        actual.oppgaveId shouldBe expected.oppgaveId
        actual.oppgavetype shouldBe expected.oppgavetype
        actual.beskrivelse shouldBe expected.beskrivelse
        actual.response shouldBe expected.response
        JSONAssert.assertEquals(actual.requestBody, expected.requestBody, true)
    }

    @Test
    fun `oppretter RETUR-oppgave for klageinstanshendelse`() {
        //language=JSON
        val expectedAttesteringRequest =
            """
                {
                  "journalpostId": null,
                  "saksreferanse": "$saksnummer",
                  "aktoerId": "$aktørId",
                  "tema": "SUP",
                  "beskrivelse": "--- 01.01.2021 02:02 - Opprettet av Supplerende Stønad ---\nSaksnummer : $saksnummer\nUtfall: Medhold\nRelevante JournalpostIDer: 123, 456\nKlageinstans sin behandling ble avsluttet den 01.01.2021 02:02\n\nKlagen krever ytterligere saksbehandling. Denne oppgaven må lukkes manuelt.",
                  "oppgavetype": "BEH_SAK",
                  "behandlingstema": "ab0431",
                  "behandlingstype": "ae0058",
                  "aktivDato": "2021-01-01",
                  "fristFerdigstillelse": "2021-01-31",
                  "prioritet": "NORM",
                  "tilordnetRessurs": null
                }
            """.trimMargin()

        val response =
            //language=JSON
            """
                        {
                          "id": 111,
                           "tildeltEnhetsnr": "4811",
                           "saksreferanse": "$søknadId",
                           "aktoerId": "$aktørId",
                           "tema": "SUP",
                           "beskrivelse": "--- 01.01.2021 02:02 - Opprettet av Supplerende Stønad ---\nSøknadId : $søknadId ",
                           "behandlingstema": "ab0431",
                           "oppgavetype": "ATT",
                           "behandlingstype": "ae0034",
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

        wireMockServer.stubFor(
            stubMapping.withRequestBody(equalToJson(expectedAttesteringRequest))
                .willReturn(aResponse().withBody(response).withStatus(201)),
        )

        val oathMock = mock<AzureAd> {
            on { onBehalfOfToken(any(), any()) } doReturn "token"
        }
        val client = OppgaveHttpClient(
            connectionConfig = ApplicationConfig.ClientsConfig.OppgaveConfig(
                clientId = "oppgaveClientId",
                url = wireMockServer.baseUrl(),
            ),
            exchange = oathMock,
            tokenoppslagForSystembruker = mock(),
            clock = fixedClock,
        )
        val actual = client.opprettOppgave(
            OppgaveConfig.Klage.Klageinstanshendelse.Handling(
                saksnummer = saksnummer,
                aktørId = AktørId(aktørId),
                tilordnetRessurs = null,
                clock = fixedClock,
                utfall = KlageinstansUtfall.MEDHOLD,
                avsluttetTidspunkt = fixedTidspunkt,
                journalpostIDer = listOf(JournalpostId("123"), JournalpostId("456")),
            ),
        ).getOrFail()

        val exoected = nyOppgaveHttpKallResponse(
            oppgaveId = OppgaveId("111"),
            oppgavetype = Oppgavetype.ATTESTERING,
            requestBody = expectedAttesteringRequest,
            response = response,
            beskrivelse = "--- 01.01.2021 02:02 - Opprettet av Supplerende Stønad ---\nSaksnummer : $saksnummer\nUtfall: Medhold\nRelevante JournalpostIDer: 123, 456\nKlageinstans sin behandling ble avsluttet den 01.01.2021 02:02\n\nKlagen krever ytterligere saksbehandling. Denne oppgaven må lukkes manuelt.",
        )

        actual.oppgaveId shouldBe exoected.oppgaveId
        actual.oppgavetype shouldBe exoected.oppgavetype
        actual.beskrivelse shouldBe exoected.beskrivelse
        actual.response shouldBe exoected.response
        JSONAssert.assertEquals(actual.requestBody, exoected.requestBody, true)
    }

    private val stubMapping = WireMock.post(urlPathEqualTo(OPPGAVE_PATH))
        .withHeader("Authorization", WireMock.equalTo("Bearer token"))
        .withHeader("X-Correlation-ID", WireMock.equalTo("correlationId"))
        .withHeader("Accept", WireMock.equalTo("application/json"))
        .withHeader("Content-Type", WireMock.equalTo("application/json"))

    @BeforeEach
    fun beforeEach() {
        MDC.put("Authorization", "Bearer token")
    }
}
