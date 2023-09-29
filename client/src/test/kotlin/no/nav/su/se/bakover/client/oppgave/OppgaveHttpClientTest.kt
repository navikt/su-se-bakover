package no.nav.su.se.bakover.client.oppgave

import arrow.core.left
import arrow.core.right
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.equalToJson
import com.github.tomakehurst.wiremock.client.WireMock.forbidden
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.patch
import com.github.tomakehurst.wiremock.client.WireMock.patchRequestedFor
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
import no.nav.su.se.bakover.common.ident.NavIdentBruker.Saksbehandler
import no.nav.su.se.bakover.common.infrastructure.config.ApplicationConfig
import no.nav.su.se.bakover.common.journal.JournalpostId
import no.nav.su.se.bakover.common.person.AktørId
import no.nav.su.se.bakover.domain.klage.KlageinstansUtfall
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import no.nav.su.se.bakover.domain.oppgave.OppgaveFeil
import no.nav.su.se.bakover.domain.sak.Sakstype
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedTidspunkt
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
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

        wireMockServer.stubFor(
            stubMapping.withRequestBody(equalToJson(expectedSaksbehandlingRequest)).willReturn(
                aResponse()
                    .withBody(
                        //language=JSON
                        """
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
                        """.trimIndent(),
                    )
                    .withStatus(201),
            ),
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

        client.opprettOppgave(
            OppgaveConfig.Søknad(
                sakstype = Sakstype.UFØRE,
                journalpostId = journalpostId,
                søknadId = søknadId,
                aktørId = AktørId(aktørId),
                tilordnetRessurs = null,
                clock = fixedClock,
            ),
        ) shouldBe OppgaveId("111").right()

        verify(oathMock).onBehalfOfToken(
            originalToken = argThat { it shouldBe "Bearer token" },
            otherAppId = argThat { it shouldBe "oppgaveClientId" },
        )
        verifyNoMoreInteractions(oathMock, tokenoppslagMock)

        client.opprettOppgaveMedSystembruker(
            OppgaveConfig.Søknad(
                sakstype = Sakstype.UFØRE,
                journalpostId = journalpostId,
                søknadId = søknadId,
                aktørId = AktørId(aktørId),
                tilordnetRessurs = null,
                clock = fixedClock,
            ),
        ) shouldBe OppgaveId("111").right()

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

        wireMockServer.stubFor(
            stubMapping.withRequestBody(equalToJson(expectedSaksbehandlingRequest)).willReturn(
                aResponse()
                    .withBody(
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
                        """.trimIndent(),
                    )
                    .withStatus(201),
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
            OppgaveConfig.Søknad(
                sakstype = Sakstype.UFØRE,
                journalpostId = journalpostId,
                søknadId = søknadId,
                aktørId = AktørId(aktørId),
                tilordnetRessurs = saksbehandler,
                clock = fixedClock,
            ),
        ) shouldBe OppgaveId("111").right()
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

        wireMockServer.stubFor(
            stubMapping.withRequestBody(equalToJson(expectedAttesteringRequest)).willReturn(
                aResponse()
                    .withBody(
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
                        """.trimIndent(),
                    )
                    .withStatus(201),
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
            OppgaveConfig.AttesterSøknadsbehandling(
                søknadId = søknadId,
                aktørId = AktørId(aktørId),
                clock = fixedClock,
                tilordnetRessurs = null,
            ),
        ) shouldBe OppgaveId("111").right()
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
    fun `lukker en oppgave med en oppgaveId`() {
        val oppgaveId = 12345L
        val versjon = 2
        wireMockServer.stubFor(
            get((urlPathEqualTo("$OPPGAVE_PATH/$oppgaveId")))
                .withHeader("Authorization", WireMock.equalTo("Bearer token"))
                .withHeader("Content-Type", WireMock.equalTo("application/json"))
                .withHeader("Accept", WireMock.equalTo("application/json"))
                .withHeader("X-Correlation-ID", WireMock.equalTo("correlationId"))
                .willReturn(
                    aResponse()
                        .withBody(
                            //language=JSON
                            """
                            {
                                      "id": $oppgaveId,
                                      "tildeltEnhetsnr": "1234",
                                      "endretAvEnhetsnr": "1234",
                                      "opprettetAvEnhetsnr": "1234",
                                      "aktoerId": "1000012345678",
                                      "saksreferanse": "$søknadId",
                                      "tilordnetRessurs": "Z123456",
                                      "tema": "SUP",
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
                            """.trimIndent(),
                        )
                        .withStatus(200),
                ),
        )

        wireMockServer.stubFor(
            patch((urlPathEqualTo("$OPPGAVE_PATH/$oppgaveId")))
                .withHeader("Authorization", WireMock.equalTo("Bearer token"))
                .withHeader("Content-Type", WireMock.equalTo("application/json"))
                .withHeader("Accept", WireMock.equalTo("application/json"))
                .withHeader("X-Correlation-ID", WireMock.equalTo("correlationId"))
                .willReturn(
                    aResponse()
                        .withBody(
                            //language=JSON
                            """
                            {
                              "id": $oppgaveId,
                              "versjon": ${versjon + 1},
                              "beskrivelse": "--- 01.01.2021 02:02 - Lukket av Supplerende Stønad ---\nSøknadId : $søknadId",
                              "status": "FERDIGSTILT"
                            }
                            """.trimIndent(),
                        )
                        .withStatus(200),
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
        client.lukkOppgave(OppgaveId(oppgaveId.toString()))

        WireMock.configureFor(wireMockServer.port())
        WireMock.verify(
            1,
            patchRequestedFor(urlPathEqualTo("$OPPGAVE_PATH/$oppgaveId"))
                .withRequestBody(
                    equalToJson(
                        //language=JSON
                        """
                            {
                              "id": $oppgaveId,
                              "versjon": $versjon,
                              "beskrivelse": "--- 01.01.2021 02:02 - Lukket av Supplerende Stønad ---",
                              "status": "FERDIGSTILT"
                            }
                        """.trimIndent(),
                    ),
                ),
        )
    }

    @Test
    fun `lukker en oppgave med en oppgaveId for en systembruker`() {
        val oppgaveId = 12345L
        val versjon = 2
        wireMockServer.stubFor(
            get((urlPathEqualTo("$OPPGAVE_PATH/$oppgaveId")))
                .withHeader("Authorization", WireMock.equalTo("Bearer token"))
                .withHeader("Content-Type", WireMock.equalTo("application/json"))
                .withHeader("Accept", WireMock.equalTo("application/json"))
                .withHeader("X-Correlation-ID", WireMock.equalTo("correlationId"))
                .willReturn(
                    aResponse()
                        .withBody(
                            //language=JSON
                            """
                            {
                                      "id": $oppgaveId,
                                      "tildeltEnhetsnr": "1234",
                                      "endretAvEnhetsnr": "1234",
                                      "opprettetAvEnhetsnr": "1234",
                                      "aktoerId": "1000012345678",
                                      "saksreferanse": "$søknadId",
                                      "tilordnetRessurs": "Z123456",
                                      "tema": "SUP",
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
                            """.trimIndent(),
                        )
                        .withStatus(200),
                ),
        )

        wireMockServer.stubFor(
            patch((urlPathEqualTo("$OPPGAVE_PATH/$oppgaveId")))
                .withHeader("Authorization", WireMock.equalTo("Bearer token"))
                .withHeader("Content-Type", WireMock.equalTo("application/json"))
                .withHeader("Accept", WireMock.equalTo("application/json"))
                .withHeader("X-Correlation-ID", WireMock.equalTo("correlationId"))
                .willReturn(
                    aResponse()
                        .withBody(
                            //language=JSON
                            """
                            {
                              "id": $oppgaveId,
                              "versjon": ${versjon + 1},
                              "beskrivelse": "--- 01.01.2021 02:02 - Lukket av Supplerende Stønad ---\nSøknadId : $søknadId",
                              "status": "FERDIGSTILT"
                            }
                            """.trimIndent(),
                        )
                        .withStatus(200),
                ),
        )

        val tokenoppslagMock = mock<TokenOppslag> {
            on { token() } doReturn AccessToken("token")
        }

        val client = OppgaveHttpClient(
            connectionConfig = ApplicationConfig.ClientsConfig.OppgaveConfig(
                clientId = "oppgaveClientId",
                url = wireMockServer.baseUrl(),
            ),
            exchange = mock(),
            tokenoppslagForSystembruker = tokenoppslagMock,
            clock = fixedClock,
        )
        client.lukkOppgaveMedSystembruker(OppgaveId(oppgaveId.toString()))

        WireMock.configureFor(wireMockServer.port())
        WireMock.verify(
            1,
            patchRequestedFor(urlPathEqualTo("$OPPGAVE_PATH/$oppgaveId"))
                .withRequestBody(
                    equalToJson(
                        //language=JSON
                        """
                            {
                              "id": $oppgaveId,
                              "versjon": $versjon,
                              "beskrivelse": "--- 01.01.2021 02:02 - Lukket av Supplerende Stønad ---",
                              "status": "FERDIGSTILT"
                            }
                        """.trimIndent(),
                    ),
                ),
        )
    }

    @Test
    fun `Legger til lukket beskrivelse på starten av beskrivelse`() {
        val oppgaveId = 12345L
        val versjon = 2
        wireMockServer.stubFor(
            get((urlPathEqualTo("$OPPGAVE_PATH/$oppgaveId")))
                .withHeader("Authorization", WireMock.equalTo("Bearer token"))
                .withHeader("Content-Type", WireMock.equalTo("application/json"))
                .withHeader("Accept", WireMock.equalTo("application/json"))
                .withHeader("X-Correlation-ID", WireMock.equalTo("correlationId"))
                .willReturn(
                    aResponse()
                        .withBody(
                            //language=JSON
                            """
                            {
                                      "id": $oppgaveId,
                                      "tildeltEnhetsnr": "1234",
                                      "endretAvEnhetsnr": "1234",
                                      "opprettetAvEnhetsnr": "1234",
                                      "aktoerId": "1000012345678",
                                      "saksreferanse": "$søknadId",
                                      "tilordnetRessurs": "Z123456",
                                      "beskrivelse": "--- 01.01.0001 01:01 Fornavn Etternavn (Z12345, 4815) ---\nforrige melding",
                                      "tema": "SUP",
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
                            """.trimIndent(),
                        )
                        .withStatus(200),
                ),
        )

        wireMockServer.stubFor(
            patch((urlPathEqualTo("$OPPGAVE_PATH/$oppgaveId")))
                .withHeader("Authorization", WireMock.equalTo("Bearer token"))
                .withHeader("Content-Type", WireMock.equalTo("application/json"))
                .withHeader("Accept", WireMock.equalTo("application/json"))
                .withHeader("X-Correlation-ID", WireMock.equalTo("correlationId"))
                .willReturn(
                    aResponse()
                        .withBody(
                            //language=JSON
                            """
                            {
                              "id": $oppgaveId,
                              "versjon": ${versjon + 1},
                              "beskrivelse": "--- 01.01.2021 02:02 - Lukket av Supplerende Stønad ---\nSøknadId : $søknadId\n\n--- 01.01.0001 01:01 Fornavn Etternavn (Z12345, 4815) ---\nforrige melding",
                              "status": "FERDIGSTILT"
                            }
                            """.trimIndent(),
                        )
                        .withStatus(200),
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
        client.lukkOppgave(OppgaveId(oppgaveId.toString()))

        WireMock.configureFor(wireMockServer.port())
        WireMock.verify(
            1,
            patchRequestedFor(urlPathEqualTo("$OPPGAVE_PATH/$oppgaveId"))
                .withRequestBody(
                    equalToJson(
                        //language=JSON
                        """
                            {
                              "id": $oppgaveId,
                              "versjon": $versjon,
                              "beskrivelse": "--- 01.01.2021 02:02 - Lukket av Supplerende Stønad ---\n\n--- 01.01.0001 01:01 Fornavn Etternavn (Z12345, 4815) ---\nforrige melding",
                              "status": "FERDIGSTILT"
                            }
                        """.trimIndent(),
                    ),
                ),
        )
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

        wireMockServer.stubFor(
            stubMapping.withRequestBody(equalToJson(expectedSaksbehandlingRequest)).willReturn(
                aResponse()
                    .withBody(
                        //language=JSON
                        """
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
                        """.trimIndent(),
                    )
                    .withStatus(201),
            ),
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

        client.opprettOppgave(
            OppgaveConfig.Revurderingsbehandling(
                saksnummer = saksnummer,
                aktørId = AktørId(aktørId),
                tilordnetRessurs = null,
                clock = fixedClock,
            ),
        ) shouldBe OppgaveId("111").right()

        verify(oathMock).onBehalfOfToken(
            originalToken = argThat { it shouldBe "Bearer token" },
            otherAppId = argThat { it shouldBe "oppgaveClientId" },
        )
        verifyNoMoreInteractions(oathMock, tokenoppslagMock)

        client.opprettOppgaveMedSystembruker(
            OppgaveConfig.Revurderingsbehandling(
                saksnummer = saksnummer,
                aktørId = AktørId(aktørId),
                tilordnetRessurs = null,
                clock = fixedClock,
            ),
        ) shouldBe OppgaveId("111").right()

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

        wireMockServer.stubFor(
            stubMapping.withRequestBody(equalToJson(expectedAttesteringRequest)).willReturn(
                aResponse()
                    .withBody(
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
                        """.trimIndent(),
                    )
                    .withStatus(201),
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
        ) shouldBe OppgaveId("111").right()
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
    fun `oppdaterer eksisterende oppgave med ny beskrivelse`() {
        val oppgaveId = 12345L
        val versjon = 2

        wireMockServer.stubFor(
            get((urlPathEqualTo("$OPPGAVE_PATH/$oppgaveId")))
                .withHeader("Authorization", WireMock.equalTo("Bearer token"))
                .withHeader("Content-Type", WireMock.equalTo("application/json"))
                .withHeader("Accept", WireMock.equalTo("application/json"))
                .withHeader("X-Correlation-ID", WireMock.equalTo("correlationId"))
                .willReturn(
                    aResponse()
                        .withBody(
                            //language=JSON
                            """
                            {
                                      "id": $oppgaveId,
                                      "tildeltEnhetsnr": "1234",
                                      "endretAvEnhetsnr": "1234",
                                      "opprettetAvEnhetsnr": "1234",
                                      "aktoerId": "1000012345678",
                                      "saksreferanse": "$søknadId",
                                      "tilordnetRessurs": "Z123456",
                                      "tema": "SUP",
                                      "oppgavetype": "BEH_SAK",
                                      "behandlingstype": "ae0034",
                                      "versjon": $versjon,
                                      "beskrivelse": "Dette er den orginale beskrivelsen",
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
                            """.trimIndent(),
                        )
                        .withStatus(200),
                ),
        )

        wireMockServer.stubFor(
            patch((urlPathEqualTo("$OPPGAVE_PATH/$oppgaveId")))
                .willReturn(
                    aResponse()
                        .withBody(
                            //language=JSON
                            """
                            {
                              "id": $oppgaveId,
                              "versjon": ${versjon + 1},
                              "beskrivelse": "--- 01.01.2021 02:02 - en beskrivelse ---",
                              "status": "AAPNET"
                            }
                            """.trimIndent(),
                        )
                        .withStatus(200),
                ),
        )

        val oauthMock = mock<AzureAd> {
            on { onBehalfOfToken(any(), any()) } doReturn "token"
        }

        val client = OppgaveHttpClient(
            connectionConfig = ApplicationConfig.ClientsConfig.OppgaveConfig(
                clientId = "oppgaveClientId",
                url = wireMockServer.baseUrl(),
            ),
            exchange = oauthMock,
            tokenoppslagForSystembruker = mock(),
            clock = fixedClock,
        )

        client.oppdaterOppgave(
            oppgaveId = OppgaveId(oppgaveId.toString()),
            beskrivelse = "en beskrivelse",
        ) shouldBe Unit.right()

        val expectedBody =
            """
            {
            "id" : $oppgaveId,
            "versjon" : $versjon,
            "beskrivelse" : "--- 01.01.2021 02:02 - en beskrivelse ---\n\nDette er den orginale beskrivelsen",
            "status" : "AAPNET"
            }
            """.trimIndent()
        wireMockServer.verify(
            1,
            patchRequestedFor(urlPathEqualTo("$OPPGAVE_PATH/$oppgaveId"))
                .withHeader("Authorization", WireMock.equalTo("Bearer token"))
                .withHeader("Content-Type", WireMock.equalTo("application/json"))
                .withHeader("Accept", WireMock.equalTo("application/json"))
                .withHeader("X-Correlation-ID", WireMock.equalTo("correlationId"))
                .withRequestBody(
                    equalToJson(expectedBody),
                ),
        )
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

        wireMockServer.stubFor(
            stubMapping.withRequestBody(equalToJson(expectedAttesteringRequest)).willReturn(
                aResponse()
                    .withBody(
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
                        """.trimIndent(),
                    )
                    .withStatus(201),
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
            OppgaveConfig.Klage.Klageinstanshendelse.Informasjon(
                saksnummer = saksnummer,
                aktørId = AktørId(aktørId),
                tilordnetRessurs = null,
                clock = fixedClock,
                utfall = KlageinstansUtfall.STADFESTELSE,
                avsluttetTidspunkt = fixedTidspunkt,
                journalpostIDer = listOf(JournalpostId("123"), JournalpostId("456")),
            ),
        ) shouldBe OppgaveId("111").right()
    }

    @Test
    fun `oppretter MEDHOLD-oppgave for klageinstanshendelse`() {
        //language=JSON
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
            """.trimMargin()

        wireMockServer.stubFor(
            stubMapping.withRequestBody(equalToJson(expectedAttesteringRequest)).willReturn(
                aResponse()
                    .withBody(
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
                        """.trimIndent(),
                    )
                    .withStatus(201),
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
            OppgaveConfig.Klage.Klageinstanshendelse.Handling(
                saksnummer = saksnummer,
                aktørId = AktørId(aktørId),
                tilordnetRessurs = null,
                clock = fixedClock,
                utfall = KlageinstansUtfall.RETUR,
                avsluttetTidspunkt = fixedTidspunkt,
                journalpostIDer = listOf(JournalpostId("123"), JournalpostId("456")),
            ),
        ) shouldBe OppgaveId("111").right()
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

        wireMockServer.stubFor(
            stubMapping.withRequestBody(equalToJson(expectedAttesteringRequest)).willReturn(
                aResponse()
                    .withBody(
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
                        """.trimIndent(),
                    )
                    .withStatus(201),
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
            OppgaveConfig.Klage.Klageinstanshendelse.Handling(
                saksnummer = saksnummer,
                aktørId = AktørId(aktørId),
                tilordnetRessurs = null,
                clock = fixedClock,
                utfall = KlageinstansUtfall.MEDHOLD,
                avsluttetTidspunkt = fixedTidspunkt,
                journalpostIDer = listOf(JournalpostId("123"), JournalpostId("456")),
            ),
        ) shouldBe OppgaveId("111").right()
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
