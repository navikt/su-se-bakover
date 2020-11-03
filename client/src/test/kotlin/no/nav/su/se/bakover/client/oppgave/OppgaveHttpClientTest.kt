package no.nav.su.se.bakover.client.oppgave

import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.equalToJson
import com.github.tomakehurst.wiremock.client.WireMock.forbidden
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.patch
import com.github.tomakehurst.wiremock.client.WireMock.patchRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.verify
import io.kotest.assertions.arrow.either.shouldBeLeft
import io.kotest.assertions.arrow.either.shouldBeRight
import no.nav.su.se.bakover.client.WiremockBase
import no.nav.su.se.bakover.client.WiremockBase.Companion.wireMockServer
import no.nav.su.se.bakover.client.stubs.sts.TokenOppslagStub
import no.nav.su.se.bakover.domain.AktørId
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.oppgave.KunneIkkeOppretteOppgave
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import org.junit.jupiter.api.Test
import java.time.LocalDate

internal class OppgaveHttpClientTest : WiremockBase {

    private val client = OppgaveHttpClient(
        wireMockServer.baseUrl(),
        TokenOppslagStub
    )

    private val aktørId = "333"
    private val journalpostId = JournalpostId("444")
    private val sakId = "222"

    @Test
    fun `opprett sakbehandling oppgave`() {
        //language=JSON
        val expectedSaksbehandlingRequest =
            """
                {
                    "journalpostId": "$journalpostId",
                    "saksreferanse": "$sakId",
                    "aktoerId": "$aktørId",
                    "tema": "SUP",
                    "behandlesAvApplikasjon": "SUPSTONAD",
                    "oppgavetype": "BEH_SAK",
                    "behandlingstema": "ab0431",
                    "behandlingstype": "ae0245",
                    "aktivDato": "${LocalDate.now()}",
                    "fristFerdigstillelse": "${LocalDate.now().plusDays(30)}",
                    "prioritet": "NORM"
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
                                                      "saksreferanse": "$sakId",
                                                      "aktoerId": "$aktørId",
                                                      "tema": "SUP",
                                                      "behandlesAvApplikasjon": "SUPSTONAD",
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
        client.opprettOppgave(
            OppgaveConfig.Saksbehandling(
                journalpostId,
                sakId,
                AktørId(aktørId)
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
                    "saksreferanse": "$sakId",
                    "aktoerId": "$aktørId",
                    "tema": "SUP",
                    "behandlesAvApplikasjon": "SUPSTONAD",
                    "oppgavetype": "ATT",
                    "behandlingstema": "ab0431",
                    "behandlingstype": "ae0245",
                    "aktivDato": "${LocalDate.now()}",
                    "fristFerdigstillelse": "${LocalDate.now().plusDays(30)}",
                    "prioritet": "NORM"
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
                                                      "saksreferanse": "$sakId",
                                                      "aktoerId": "$aktørId",
                                                      "tema": "SUP",
                                                      "behandlesAvApplikasjon": "SUPSTONAD",
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
        client.opprettOppgave(
            OppgaveConfig.Attestering(
                sakId = sakId,
                aktørId = AktørId(aktørId)
            )
        ) shouldBeRight OppgaveId("111")
    }

    @Test
    fun `returns KunneIkkeOppretteOppgave`() {
        wireMockServer.stubFor(stubMapping.willReturn(forbidden()))

        client.opprettOppgave(
            OppgaveConfig.Saksbehandling(
                journalpostId,
                sakId,
                AktørId(aktørId)
            )
        ) shouldBeLeft KunneIkkeOppretteOppgave
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
                                      "tilordnetRessurs": "Z123456",
                                      "beskrivelse": "MASKERT",
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
                              "status": "FERDIGSTILT"
                            }
                            """.trimIndent()
                        )
                        .withStatus(200)
                )
        )

        client.lukkOppgave(OppgaveId(oppgaveId.toString()))

        WireMock.configureFor(wireMockServer.port())
        verify(
            1,
            patchRequestedFor(urlPathEqualTo("$oppgavePath/$oppgaveId"))
                .withRequestBody(
                    equalToJson(
                        //language=JSON
                        """
                            {
                              "id": $oppgaveId,
                              "versjon": $versjon,
                              "status": "FERDIGSTILT"
                            }
                        """.trimIndent()
                    )
                )
        )
    }

    private val stubMapping = WireMock.post(WireMock.urlPathEqualTo(oppgavePath))
        .withHeader("Authorization", WireMock.equalTo("Bearer token"))
        .withHeader("X-Correlation-ID", WireMock.equalTo("correlationId"))
        .withHeader("Accept", WireMock.equalTo("application/json"))
        .withHeader("Content-Type", WireMock.equalTo("application/json"))
}
