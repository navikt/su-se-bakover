package no.nav.su.se.bakover.client.oppgave

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.equalToJson
import com.github.tomakehurst.wiremock.client.WireMock.forbidden
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import no.nav.su.meldinger.kafka.soknad.NySøknadMedJournalId
import no.nav.su.se.bakover.client.stubs.TokenOppslagStub
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime

internal class OppgaveClientTest {

    private val client = OppgaveClient(wireMockServer.baseUrl(), TokenOppslagStub)
    private val nySøknadMedJournalId = NySøknadMedJournalId(
        correlationId = "correlationId",
        fnr = "12345678910",
        søknadId = "111",
        søknad = "",
        sakId = "222",
        aktørId = "333",
        journalId = "444"
    )

    @Test
    fun `should opprett oppgave`() {
        wireMockServer.stubFor(
            stubMapping.withRequestBody(equalToJson(expectedRequest)).willReturn(
                WireMock.aResponse()
                    .withBody(
                        //language=JSON
                        """
                                    {
                                                      "id": 111,
                                                      "tildeltEnhetsnr": "4811",
                                                      "journalpostId": "${nySøknadMedJournalId.journalId}",
                                                      "saksreferanse": "${nySøknadMedJournalId.sakId}",
                                                      "aktoerId": "${nySøknadMedJournalId.aktørId}",
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
        client.opprettOppgave(nySøknadMedJournalId) shouldBe 111
    }

    @Test
    fun `should throw exception if error`() {
        wireMockServer.stubFor(stubMapping.willReturn(forbidden()))
        val exception = shouldThrow<RuntimeException> {
            client.opprettOppgave(nySøknadMedJournalId)
        }
        exception.message shouldBe "Feil i kallet mot oppgave"
    }

    //language=JSON
    private val expectedRequest = """
{ 
    "journalpostId": "${nySøknadMedJournalId.journalId}",
    "saksreferanse": "${nySøknadMedJournalId.sakId}",
    "aktoerId": "${nySøknadMedJournalId.aktørId}", 
    "tema": "SUP",
    "behandlesAvApplikasjon": "SUPSTONAD",
    "oppgavetype": "BEH_SAK",
    "behandlingstema": "ab0431", 
    "behandlingstype": "ae0245", 
    "aktivDato": "${LocalDate.now()}",
    "fristFerdigstillelse": "${LocalDate.now().plusDays(30)}",
    "prioritet": "NORM"
}""".trimMargin()

    private val stubMapping = WireMock.post(WireMock.urlPathEqualTo(oppgavePath))
        .withHeader("Authorization", WireMock.equalTo("Bearer token"))
        .withHeader("X-Correlation-ID", WireMock.equalTo("correlationId"))
        .withHeader("Accept", WireMock.equalTo("application/json"))
        .withHeader("Content-Type", WireMock.equalTo("application/json"))

    companion object {
        val wireMockServer: WireMockServer = WireMockServer(WireMockConfiguration.options().dynamicPort())

        @BeforeAll
        @JvmStatic
        fun start() {
            wireMockServer.start()
        }

        @AfterAll
        @JvmStatic
        fun stop() {
            wireMockServer.stop()
        }
    }
}
