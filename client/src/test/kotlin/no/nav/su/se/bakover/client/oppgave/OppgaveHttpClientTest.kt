package no.nav.su.se.bakover.client.oppgave

import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.equalToJson
import com.github.tomakehurst.wiremock.client.WireMock.forbidden
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.patch
import com.github.tomakehurst.wiremock.client.WireMock.patchRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.http.Fault
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyNoMoreInteractions
import io.kotest.assertions.arrow.either.shouldBeLeft
import io.kotest.assertions.arrow.either.shouldBeRight
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.client.WiremockBase
import no.nav.su.se.bakover.client.WiremockBase.Companion.wireMockServer
import no.nav.su.se.bakover.client.argThat
import no.nav.su.se.bakover.client.azure.OAuth
import no.nav.su.se.bakover.client.sts.TokenOppslag
import no.nav.su.se.bakover.common.ApplicationConfig
import no.nav.su.se.bakover.common.zoneIdOslo
import no.nav.su.se.bakover.domain.AktørId
import no.nav.su.se.bakover.domain.NavIdentBruker.Saksbehandler
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import no.nav.su.se.bakover.domain.oppgave.OppgaveFeil
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.MDC
import java.time.Clock
import java.time.Instant
import java.util.UUID

internal class OppgaveHttpClientTest : WiremockBase {

    private val fixedEpochClock = Clock.fixed(Instant.EPOCH, zoneIdOslo)
    private val saksbehandler = Saksbehandler("Z12345")
    private val aktørId = "333"
    private val journalpostId = JournalpostId("444")
    private val søknadId = UUID.randomUUID()
    private val saksnummer = Saksnummer(12345)

    @Test
    fun `opprett sakbehandling oppgave`() {
        //language=JSON
        val expectedSaksbehandlingRequest =
            """
                {
                    "journalpostId": "$journalpostId",
                    "saksreferanse": "$søknadId",
                    "aktoerId": "$aktørId",
                    "tema": "SUP",
                    "behandlesAvApplikasjon": "SUPSTONAD",
                    "beskrivelse": "--- 01.01.1970 01:00 - Opprettet av Supplerende Stønad ---\nSøknadId : $søknadId",
                    "oppgavetype": "BEH_SAK",
                    "behandlingstema": "ab0431",
                    "behandlingstype": "ae0245",
                    "aktivDato": "1970-01-01",
                    "fristFerdigstillelse": "1970-01-31",
                    "prioritet": "NORM",
                    "tilordnetRessurs": null
                }""".trimMargin()

        wireMockServer.stubFor(
            stubMapping.withRequestBody(equalToJson(expectedSaksbehandlingRequest)).willReturn(
                WireMock.aResponse()
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
                                                      "behandlesAvApplikasjon": "SUPSTONAD",
                                                      "beskrivelse": "--- 01.01.1970 01:00 - Opprettet av Supplerende Stønad ---\nSøknadId : $søknadId ",
                                                      "behandlingstema": "ab0431",
                                                      "oppgavetype": "BEH_SAK",
                                                      "behandlingstype": "ae0245",
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
                    )
                    .withStatus(201)
            )
        )

        val oathMock = mock<OAuth> {
            on { onBehalfOfToken(any(), any()) } doReturn "token"
        }

        val tokenoppslagMock = mock<TokenOppslag> {
            on { token() } doReturn "token"
        }
        val client = OppgaveHttpClient(
            connectionConfig = ApplicationConfig.ClientsConfig.OppgaveConfig(
                clientId = "oppgaveClientId",
                url = wireMockServer.baseUrl(),
            ),
            exchange = oathMock,
            tokenoppslagForSystembruker = tokenoppslagMock,
            clock = fixedEpochClock,
        )

        client.opprettOppgave(
            OppgaveConfig.Saksbehandling(
                journalpostId,
                søknadId,
                AktørId(aktørId)
            )
        ) shouldBeRight OppgaveId("111")

        verify(oathMock).onBehalfOfToken(
            originalToken = argThat { it shouldBe "Bearer token" },
            otherAppId = argThat { it shouldBe "oppgaveClientId" },
        )
        verifyNoMoreInteractions(oathMock, tokenoppslagMock)

        client.opprettOppgaveMedSystembruker(
            OppgaveConfig.Saksbehandling(
                journalpostId,
                søknadId,
                AktørId(aktørId)
            )
        ) shouldBeRight OppgaveId("111")

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
                    "behandlesAvApplikasjon": "SUPSTONAD",
                    "beskrivelse": "--- 01.01.1970 01:00 - Opprettet av Supplerende Stønad ---\nSøknadId : $søknadId",
                    "oppgavetype": "BEH_SAK",
                    "behandlingstema": "ab0431",
                    "behandlingstype": "ae0245",
                    "aktivDato": "1970-01-01",
                    "fristFerdigstillelse": "1970-01-31",
                    "prioritet": "NORM",
                    "tilordnetRessurs": "$saksbehandler"
                }""".trimMargin()

        wireMockServer.stubFor(
            stubMapping.withRequestBody(equalToJson(expectedSaksbehandlingRequest)).willReturn(
                WireMock.aResponse()
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
                                                      "behandlesAvApplikasjon": "SUPSTONAD",
                                                      "behandlingstema": "ab0431",
                                                      "beskrivelse": "--- 01.01.1970 01:00 - Opprettet av Supplerende Stønad ---\nSøknadId : $søknadId",
                                                      "oppgavetype": "BEH_SAK",
                                                      "behandlingstype": "ae0245",
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
                    )
                    .withStatus(201)
            )
        )
        val oathMock = mock<OAuth> {
            on { onBehalfOfToken(any(), any()) } doReturn "token"
        }

        val client = OppgaveHttpClient(
            connectionConfig = ApplicationConfig.ClientsConfig.OppgaveConfig(
                clientId = "oppgaveClientId",
                url = wireMockServer.baseUrl(),
            ),
            exchange = oathMock,
            tokenoppslagForSystembruker = mock(),
            clock = fixedEpochClock,
        )
        client.opprettOppgave(
            OppgaveConfig.Saksbehandling(
                journalpostId = journalpostId,
                søknadId = søknadId,
                aktørId = AktørId(aktørId),
                tilordnetRessurs = saksbehandler
            )
        ) shouldBeRight OppgaveId("111")
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
                    "behandlesAvApplikasjon": "SUPSTONAD",
                    "beskrivelse": "--- 01.01.1970 01:00 - Opprettet av Supplerende Stønad ---\nSøknadId : $søknadId",
                    "oppgavetype": "ATT",
                    "behandlingstema": "ab0431",
                    "behandlingstype": "ae0245",
                    "aktivDato": "1970-01-01",
                    "fristFerdigstillelse": "1970-01-31",
                    "prioritet": "NORM",
                    "tilordnetRessurs": null
                }""".trimMargin()

        wireMockServer.stubFor(
            stubMapping.withRequestBody(equalToJson(expectedAttesteringRequest)).willReturn(
                WireMock.aResponse()
                    .withBody(
                        //language=JSON
                        """
                                    {
                                                      "id": 111,
                                                      "tildeltEnhetsnr": "4811",
                                                      "saksreferanse": "$søknadId",
                                                      "aktoerId": "$aktørId",
                                                      "tema": "SUP",
                                                      "behandlesAvApplikasjon": "SUPSTONAD",
                                                      "beskrivelse": "--- 01.01.1970 01:00 - Opprettet av Supplerende Stønad ---\nSøknadId : $søknadId ",
                                                      "behandlingstema": "ab0431",
                                                      "oppgavetype": "ATT",
                                                      "behandlingstype": "ae0245",
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
                    )
                    .withStatus(201)
            )
        )

        val oathMock = mock<OAuth> {
            on { onBehalfOfToken(any(), any()) } doReturn "token"
        }
        val client = OppgaveHttpClient(
            connectionConfig = ApplicationConfig.ClientsConfig.OppgaveConfig(
                clientId = "oppgaveClientId",
                url = wireMockServer.baseUrl(),
            ),
            exchange = oathMock,
            tokenoppslagForSystembruker = mock(),
            clock = fixedEpochClock,
        )
        client.opprettOppgave(
            OppgaveConfig.Attestering(
                søknadId = søknadId,
                aktørId = AktørId(aktørId)
            )
        ) shouldBeRight OppgaveId("111")
    }

    @Test
    fun `returns KunneIkkeOppretteOppgave`() {
        wireMockServer.stubFor(stubMapping.willReturn(forbidden()))

        val oathMock = mock<OAuth> {
            on { onBehalfOfToken(any(), any()) } doReturn "token"
        }
        val client = OppgaveHttpClient(
            connectionConfig = ApplicationConfig.ClientsConfig.OppgaveConfig(
                clientId = "oppgaveClientId",
                url = wireMockServer.baseUrl(),
            ),
            exchange = oathMock,
            tokenoppslagForSystembruker = mock(),
            clock = fixedEpochClock,
        )
        client.opprettOppgave(
            OppgaveConfig.Saksbehandling(
                journalpostId,
                søknadId,
                AktørId(aktørId),
            ),
        ) shouldBeLeft OppgaveFeil.KunneIkkeOppretteOppgave
    }

    @Test
    fun `lukker en oppgave med en oppgaveId`() {
        val oppgaveId = 12345L
        val versjon = 2
        wireMockServer.stubFor(
            get((urlPathEqualTo("$oppgavePath/$oppgaveId")))
                .withHeader("Authorization", WireMock.equalTo("Bearer token"))
                .withHeader("Content-Type", WireMock.equalTo("application/json"))
                .withHeader("Accept", WireMock.equalTo("application/json"))
                .withHeader("X-Correlation-ID", WireMock.equalTo("correlationId"))
                .willReturn(
                    WireMock.aResponse()
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
                                      "behandlingstype": "ae0245",
                                      "versjon": $versjon,
                                      "opprettetAv": "supstonad",
                                      "endretAv": "supstonad",
                                      "prioritet": "NORM",
                                      "status": "AAPNET",
                                      "metadata": {},
                                      "fristFerdigstillelse": "2019-01-04",
                                      "aktivDato": "2019-01-04",
                                      "opprettetTidspunkt": "2019-01-04T09:53:39.329+01:00",
                                      "endretTidspunkt": "2019-08-25T11:45:38+02:00"
                                    }
                            """.trimIndent()
                        )
                        .withStatus(200)
                )
        )

        wireMockServer.stubFor(
            patch((urlPathEqualTo("$oppgavePath/$oppgaveId")))
                .withHeader("Authorization", WireMock.equalTo("Bearer token"))
                .withHeader("Content-Type", WireMock.equalTo("application/json"))
                .withHeader("Accept", WireMock.equalTo("application/json"))
                .withHeader("X-Correlation-ID", WireMock.equalTo("correlationId"))
                .willReturn(
                    WireMock.aResponse()
                        .withBody(
                            //language=JSON
                            """
                            {
                              "id": $oppgaveId,
                              "versjon": ${versjon + 1},
                              "beskrivelse": "--- 01.01.1970 01:00 - Lukket av Supplerende Stønad ---\nSøknadId : $søknadId",
                              "status": "FERDIGSTILT"
                            }
                            """.trimIndent()
                        )
                        .withStatus(200)
                )
        )

        val oathMock = mock<OAuth> {
            on { onBehalfOfToken(any(), any()) } doReturn "token"
        }
        val client = OppgaveHttpClient(
            connectionConfig = ApplicationConfig.ClientsConfig.OppgaveConfig(
                clientId = "oppgaveClientId",
                url = wireMockServer.baseUrl(),
            ),
            exchange = oathMock,
            tokenoppslagForSystembruker = mock(),
            clock = fixedEpochClock,
        )
        client.lukkOppgave(OppgaveId(oppgaveId.toString()))

        WireMock.configureFor(wireMockServer.port())
        WireMock.verify(
            1,
            patchRequestedFor(urlPathEqualTo("$oppgavePath/$oppgaveId"))
                .withRequestBody(
                    equalToJson(
                        //language=JSON
                        """
                            {
                              "id": $oppgaveId,
                              "versjon": $versjon,
                              "beskrivelse": "--- 01.01.1970 01:00 - Lukket av Supplerende Stønad ---\nSøknadId : $søknadId",
                              "status": "FERDIGSTILT"
                            }
                        """.trimIndent()
                    )
                )
        )
    }

    @Test
    fun `lukker en oppgave med en oppgaveId for en systembruker`() {
        val oppgaveId = 12345L
        val versjon = 2
        wireMockServer.stubFor(
            get((urlPathEqualTo("$oppgavePath/$oppgaveId")))
                .withHeader("Authorization", WireMock.equalTo("Bearer token"))
                .withHeader("Content-Type", WireMock.equalTo("application/json"))
                .withHeader("Accept", WireMock.equalTo("application/json"))
                .withHeader("X-Correlation-ID", WireMock.equalTo("correlationId"))
                .willReturn(
                    WireMock.aResponse()
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
                                      "behandlingstype": "ae0245",
                                      "versjon": $versjon,
                                      "opprettetAv": "supstonad",
                                      "endretAv": "supstonad",
                                      "prioritet": "NORM",
                                      "status": "AAPNET",
                                      "metadata": {},
                                      "fristFerdigstillelse": "2019-01-04",
                                      "aktivDato": "2019-01-04",
                                      "opprettetTidspunkt": "2019-01-04T09:53:39.329+01:00",
                                      "endretTidspunkt": "2019-08-25T11:45:38+02:00"
                                    }
                            """.trimIndent()
                        )
                        .withStatus(200)
                )
        )

        wireMockServer.stubFor(
            patch((urlPathEqualTo("$oppgavePath/$oppgaveId")))
                .withHeader("Authorization", WireMock.equalTo("Bearer token"))
                .withHeader("Content-Type", WireMock.equalTo("application/json"))
                .withHeader("Accept", WireMock.equalTo("application/json"))
                .withHeader("X-Correlation-ID", WireMock.equalTo("correlationId"))
                .willReturn(
                    WireMock.aResponse()
                        .withBody(
                            //language=JSON
                            """
                            {
                              "id": $oppgaveId,
                              "versjon": ${versjon + 1},
                              "beskrivelse": "--- 01.01.1970 01:00 - Lukket av Supplerende Stønad ---\nSøknadId : $søknadId",
                              "status": "FERDIGSTILT"
                            }
                            """.trimIndent()
                        )
                        .withStatus(200)
                )
        )

        val tokenoppslagMock = mock<TokenOppslag> {
            on { token() } doReturn "token"
        }

        val client = OppgaveHttpClient(
            connectionConfig = ApplicationConfig.ClientsConfig.OppgaveConfig(
                clientId = "oppgaveClientId",
                url = wireMockServer.baseUrl(),
            ),
            exchange = mock(),
            tokenoppslagForSystembruker = tokenoppslagMock,
            clock = fixedEpochClock,
        )
        client.lukkOppgaveMedSystembruker(OppgaveId(oppgaveId.toString()))

        WireMock.configureFor(wireMockServer.port())
        WireMock.verify(
            1,
            patchRequestedFor(urlPathEqualTo("$oppgavePath/$oppgaveId"))
                .withRequestBody(
                    equalToJson(
                        //language=JSON
                        """
                            {
                              "id": $oppgaveId,
                              "versjon": $versjon,
                              "beskrivelse": "--- 01.01.1970 01:00 - Lukket av Supplerende Stønad ---\nSøknadId : $søknadId",
                              "status": "FERDIGSTILT"
                            }
                        """.trimIndent()
                    )
                )
        )
    }

    @Test
    fun `Legger til lukket beskrivelse på starten av beskrivelse`() {
        val oppgaveId = 12345L
        val versjon = 2
        wireMockServer.stubFor(
            get((urlPathEqualTo("$oppgavePath/$oppgaveId")))
                .withHeader("Authorization", WireMock.equalTo("Bearer token"))
                .withHeader("Content-Type", WireMock.equalTo("application/json"))
                .withHeader("Accept", WireMock.equalTo("application/json"))
                .withHeader("X-Correlation-ID", WireMock.equalTo("correlationId"))
                .willReturn(
                    WireMock.aResponse()
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
                                      "behandlingstype": "ae0245",
                                      "versjon": $versjon,
                                      "opprettetAv": "supstonad",
                                      "endretAv": "supstonad",
                                      "prioritet": "NORM",
                                      "status": "AAPNET",
                                      "metadata": {},
                                      "fristFerdigstillelse": "2019-01-04",
                                      "aktivDato": "2019-01-04",
                                      "opprettetTidspunkt": "2019-01-04T09:53:39.329+01:00",
                                      "endretTidspunkt": "2019-08-25T11:45:38+02:00"
                                    }
                            """.trimIndent()
                        )
                        .withStatus(200)
                )
        )

        wireMockServer.stubFor(
            patch((urlPathEqualTo("$oppgavePath/$oppgaveId")))
                .withHeader("Authorization", WireMock.equalTo("Bearer token"))
                .withHeader("Content-Type", WireMock.equalTo("application/json"))
                .withHeader("Accept", WireMock.equalTo("application/json"))
                .withHeader("X-Correlation-ID", WireMock.equalTo("correlationId"))
                .willReturn(
                    WireMock.aResponse()
                        .withBody(
                            //language=JSON
                            """
                            {
                              "id": $oppgaveId,
                              "versjon": ${versjon + 1},
                              "beskrivelse": "--- 01.01.1970 01:00 - Lukket av Supplerende Stønad ---\nSøknadId : $søknadId\n\n--- 01.01.0001 01:01 Fornavn Etternavn (Z12345, 4815) ---\nforrige melding",
                              "status": "FERDIGSTILT"
                            }
                            """.trimIndent()
                        )
                        .withStatus(200)
                )
        )

        val oathMock = mock<OAuth> {
            on { onBehalfOfToken(any(), any()) } doReturn "token"
        }
        val client = OppgaveHttpClient(
            connectionConfig = ApplicationConfig.ClientsConfig.OppgaveConfig(
                clientId = "oppgaveClientId",
                url = wireMockServer.baseUrl(),
            ),
            exchange = oathMock,
            tokenoppslagForSystembruker = mock(),
            clock = fixedEpochClock,
        )
        client.lukkOppgave(OppgaveId(oppgaveId.toString()))

        WireMock.configureFor(wireMockServer.port())
        WireMock.verify(
            1,
            patchRequestedFor(urlPathEqualTo("$oppgavePath/$oppgaveId"))
                .withRequestBody(
                    equalToJson(
                        //language=JSON
                        """
                            {
                              "id": $oppgaveId,
                              "versjon": $versjon,
                              "beskrivelse": "--- 01.01.1970 01:00 - Lukket av Supplerende Stønad ---\nSøknadId : $søknadId\n\n--- 01.01.0001 01:01 Fornavn Etternavn (Z12345, 4815) ---\nforrige melding",
                              "status": "FERDIGSTILT"
                            }
                        """.trimIndent()
                    )
                )
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
                    "behandlesAvApplikasjon": "SUPSTONAD",
                    "beskrivelse": "--- 01.01.1970 01:00 - Opprettet av Supplerende Stønad ---\nSaksnummer : $saksnummer",
                    "oppgavetype": "BEH_SAK",
                    "behandlingstema": "ab0431",
                    "behandlingstype": "ae0028",
                    "aktivDato": "1970-01-01",
                    "fristFerdigstillelse": "1970-01-31",
                    "prioritet": "NORM",
                    "tilordnetRessurs": null
                }""".trimMargin()

        wireMockServer.stubFor(
            stubMapping.withRequestBody(equalToJson(expectedSaksbehandlingRequest)).willReturn(
                WireMock.aResponse()
                    .withBody(
                        //language=JSON
                        """
                                    {
                                                      "id": 111,
                                                      "tildeltEnhetsnr": "4811",
                                                      "saksreferanse": "$søknadId",
                                                      "aktoerId": "$aktørId",
                                                      "tema": "SUP",
                                                      "behandlesAvApplikasjon": "SUPSTONAD",
                                                      "beskrivelse": "--- 01.01.1970 01:00 - Opprettet av Supplerende Stønad ---\nSaksnummer : $saksnummer ",
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
                        """.trimIndent()
                    )
                    .withStatus(201)
            )
        )

        val oathMock = mock<OAuth> {
            on { onBehalfOfToken(any(), any()) } doReturn "token"
        }

        val tokenoppslagMock = mock<TokenOppslag> {
            on { token() } doReturn "token"
        }
        val client = OppgaveHttpClient(
            connectionConfig = ApplicationConfig.ClientsConfig.OppgaveConfig(
                clientId = "oppgaveClientId",
                url = wireMockServer.baseUrl(),
            ),
            exchange = oathMock,
            tokenoppslagForSystembruker = tokenoppslagMock,
            clock = fixedEpochClock,
        )

        client.opprettOppgave(
            OppgaveConfig.Revurderingsbehandling(
                saksnummer = saksnummer, aktørId = AktørId(aktørId), tilordnetRessurs = null
            )
        ) shouldBeRight OppgaveId("111")

        verify(oathMock).onBehalfOfToken(
            originalToken = argThat { it shouldBe "Bearer token" },
            otherAppId = argThat { it shouldBe "oppgaveClientId" },
        )
        verifyNoMoreInteractions(oathMock, tokenoppslagMock)

        client.opprettOppgaveMedSystembruker(
            OppgaveConfig.Revurderingsbehandling(
                saksnummer = saksnummer, aktørId = AktørId(aktørId), tilordnetRessurs = null
            )
        ) shouldBeRight OppgaveId("111")

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
                    "behandlesAvApplikasjon": "SUPSTONAD",
                    "beskrivelse": "--- 01.01.1970 01:00 - Opprettet av Supplerende Stønad ---\nSaksnummer : $saksnummer",
                    "oppgavetype": "ATT",
                    "behandlingstema": "ab0431",
                    "behandlingstype": "ae0028",
                    "aktivDato": "1970-01-01",
                    "fristFerdigstillelse": "1970-01-31",
                    "prioritet": "NORM",
                    "tilordnetRessurs": null
                }""".trimMargin()

        wireMockServer.stubFor(
            stubMapping.withRequestBody(equalToJson(expectedAttesteringRequest)).willReturn(
                WireMock.aResponse()
                    .withBody(
                        //language=JSON
                        """
                                    {
                                                      "id": 111,
                                                      "tildeltEnhetsnr": "4811",
                                                      "saksreferanse": "$saksnummer",
                                                      "aktoerId": "$aktørId",
                                                      "tema": "SUP",
                                                      "behandlesAvApplikasjon": "SUPSTONAD",
                                                      "beskrivelse": "--- 01.01.1970 01:00 - Opprettet av Supplerende Stønad ---\nSaksnummer : $saksnummer",
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
                    )
                    .withStatus(201)
            )
        )

        val oathMock = mock<OAuth> {
            on { onBehalfOfToken(any(), any()) } doReturn "token"
        }
        val client = OppgaveHttpClient(
            connectionConfig = ApplicationConfig.ClientsConfig.OppgaveConfig(
                clientId = "oppgaveClientId",
                url = wireMockServer.baseUrl(),
            ),
            exchange = oathMock,
            tokenoppslagForSystembruker = mock(),
            clock = fixedEpochClock,
        )
        client.opprettOppgave(
            OppgaveConfig.AttesterRevurdering(
                saksnummer = saksnummer,
                aktørId = AktørId(aktørId),
                tilordnetRessurs = null,
            ),
        ) shouldBeRight OppgaveId("111")
    }

    @Test
    fun `opprett oppgave feiler med connection reset by peer`() {
        wireMockServer.stubFor(
            WireMock.post(
                urlPathEqualTo(oppgavePath),
            ).willReturn(
                aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER),
            ),
        )

        val oathMock = mock<OAuth> {
            on { onBehalfOfToken(any(), any()) } doReturn "token"
        }
        val client = OppgaveHttpClient(
            connectionConfig = ApplicationConfig.ClientsConfig.OppgaveConfig(
                clientId = "oppgaveClientId",
                url = wireMockServer.baseUrl(),
            ),
            exchange = oathMock,
            tokenoppslagForSystembruker = mock(),
            clock = fixedEpochClock,
        )
        client.opprettOppgave(
            OppgaveConfig.AttesterRevurdering(
                saksnummer = saksnummer,
                aktørId = AktørId(aktørId),
                tilordnetRessurs = null,
            ),
        ) shouldBeLeft KunneIkkeOppretteOppgave
    }

    private val stubMapping = WireMock.post(urlPathEqualTo(oppgavePath))
        .withHeader("Authorization", WireMock.equalTo("Bearer token"))
        .withHeader("X-Correlation-ID", WireMock.equalTo("correlationId"))
        .withHeader("Accept", WireMock.equalTo("application/json"))
        .withHeader("Content-Type", WireMock.equalTo("application/json"))

    @BeforeEach
    fun beforeEach() {
        MDC.put("Authorization", "Bearer token")
    }
}
