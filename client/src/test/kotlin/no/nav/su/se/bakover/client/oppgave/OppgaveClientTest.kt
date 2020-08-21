package no.nav.su.se.bakover.client.oppgave

import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.equalToJson
import com.github.tomakehurst.wiremock.client.WireMock.forbidden
import io.kotest.assertions.arrow.either.shouldBeLeft
import io.kotest.assertions.arrow.either.shouldBeRight
import no.nav.su.se.bakover.client.ClientError
import no.nav.su.se.bakover.client.WiremockBase
import no.nav.su.se.bakover.client.WiremockBase.Companion.wireMockServer
import no.nav.su.se.bakover.client.stubs.sts.TokenOppslagStub
import no.nav.su.se.bakover.domain.AktørId
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime

internal class OppgaveClientTest : WiremockBase {

    private val client = OppgaveClient(
        wireMockServer.baseUrl(),
        TokenOppslagStub
    )

    private val aktørId = "333"
    private val journalId = "444"
    private val sakId = "222"

    @Test
    fun `opprett sakbehandling oppgave`() {
        //language=JSON
        val expectedSaksbehandlingRequest =
            """
                {
                    "journalpostId": "$journalId",
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
                                                      "journalpostId": "$journalId",
                                                      "saksreferanse": "$sakId",
                                                      "aktoerId": "$aktørId",
                                                      "tema": "SUP",
                                                      "behandlesAvApplikasjon": "SUPSTONAD",
                                                      "behandlingstema": "ab0431",
                                                      "oppgavetype": "BEH_SAK",
                                                      "behandlingstype": "ae0245",
                                                      "versjon": 1,
                                                      "fristFerdigstillelse": "2020-06-06",
                                                      "aktivDato": "${LocalDate.now()}",
                                                      "opprettetTidspunkt": "${LocalDateTime.now()}",
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
                journalId,
                sakId,
                AktørId(aktørId)
            )
        ) shouldBeRight 111
    }

    @Test
    fun `opprett attestering oppgave`() {
        //language=JSON
        val expectedAttesteringRequest =
            """
                {
                    "journalpostId": "$journalId",
                    "saksreferanse": "$sakId",
                    "aktoerId": "$aktørId",
                    "tema": "SUP",
                    "behandlesAvApplikasjon": "SUPSTONAD",
                    "oppgavetype": "ATT",
                    "behandlingstema": "ab0431",
                    "behandlingstype": "ae0004",
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
                                                      "journalpostId": "$journalId",
                                                      "saksreferanse": "$sakId",
                                                      "aktoerId": "$aktørId",
                                                      "tema": "SUP",
                                                      "behandlesAvApplikasjon": "SUPSTONAD",
                                                      "behandlingstema": "ab0431",
                                                      "oppgavetype": "ATT",
                                                      "behandlingstype": "ae0004",
                                                      "versjon": 1,
                                                      "fristFerdigstillelse": "2020-06-06",
                                                      "aktivDato": "${LocalDate.now()}",
                                                      "opprettetTidspunkt": "${LocalDateTime.now()}",
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
                journalId,
                sakId,
                AktørId(aktørId)
            )
        ) shouldBeRight 111
    }

    @Test
    fun `returns ClientError`() {
        wireMockServer.stubFor(stubMapping.willReturn(forbidden()))

        client.opprettOppgave(
            OppgaveConfig.Saksbehandling(
                journalId,
                sakId,
                AktørId(aktørId)
            )
        ) shouldBeLeft ClientError(403, "Feil i kallet mot oppgave")
    }

    private val stubMapping = WireMock.post(WireMock.urlPathEqualTo(oppgavePath))
        .withHeader("Authorization", WireMock.equalTo("Bearer token"))
        .withHeader("X-Correlation-ID", WireMock.equalTo("correlationId"))
        .withHeader("Accept", WireMock.equalTo("application/json"))
        .withHeader("Content-Type", WireMock.equalTo("application/json"))
}
