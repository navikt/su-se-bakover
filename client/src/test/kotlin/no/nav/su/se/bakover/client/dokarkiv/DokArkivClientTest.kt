package no.nav.su.se.bakover.client.dokarkiv

import arrow.core.left
import arrow.core.right
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import io.kotest.matchers.shouldBe
import no.nav.su.meldinger.kafka.soknad.NySøknad
import no.nav.su.meldinger.kafka.soknad.SøknadInnholdTestdataBuilder
import no.nav.su.se.bakover.client.ClientError
import no.nav.su.se.bakover.client.stubs.pdf.PdfGeneratorStub
import no.nav.su.se.bakover.client.stubs.sts.TokenOppslagStub
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.util.Base64

internal class DokArkivClientTest {

    val nySøknad = NySøknad(
        sakId = "1",
        søknadId = "1",
        søknad = SøknadInnholdTestdataBuilder.build().toJson(),
        fnr = "12312312312",
        aktørId = "9876543210",
        correlationId = "correlationId"
    )

    val pdf = PdfGeneratorStub.genererPdf(nySøknad)

    val forventetRequest = """
        {
          "tittel": "Søknad om supplerende stønad for uføre flyktninger",
          "journalpostType": "INNGAAENDE",
          "tema": "SUP",
          "kanal": "NAV_NO",
          "behandlingstema": "ab0268",
          "journalfoerendeEnhet": "9999",
          "avsenderMottaker": {
            "id": "${nySøknad.fnr}",
            "idType": "FNR",
            "navn": "Nordmann, Ola Erik"
          },
          "bruker": {
            "id": "${nySøknad.fnr}",
            "idType": "FNR"
          },
          "sak": {
            "fagsakId": "${nySøknad.sakId}",
            "fagsaksystem": "SUPSTONAD",
            "sakstype": "FAGSAK"
          },
          "dokumenter": [
            {
              "tittel": "Søknad om supplerende stønad for uføre flyktninger",
              "dokumentvarianter": [
                {
                  "filtype": "PDFA",
                  "fysiskDokument": "${Base64.getEncoder().encodeToString(pdf)}",
                  "variantformat": "ARKIV"
                },
                {
                  "filtype": "JSON",
                  "fysiskDokument": "${Base64.getEncoder().encodeToString(nySøknad.søknad.toByteArray())}",
                  "variantformat": "ORIGINAL"
                }
              ]
            }
          ]
        }
    """.trimIndent()

    @Test
    fun `should send pdf to journal`() {
        wireMockServer.stubFor(
            wiremockBuilder
                .withRequestBody(WireMock.equalToJson(forventetRequest))
                .willReturn(
                    WireMock.okJson(
                        """
                        {
                          "journalpostId": "1",
                          "journalpostferdigstilt": true,
                          "dokumenter": [
                            {
                              "dokumentInfoId": "485227498",
                              "tittel": "Søknad om supplerende stønad for uføre flyktninger"
                            }
                          ]
                        }
                    """.trimIndent()
                    )
                )
        )
        val client = DokArkivClient(
            wireMockServer.baseUrl(),
            TokenOppslagStub
        )

        client.opprettJournalpost(nySøknad, pdf).shouldBe(
            "1".right()
        )
    }

    @Test
    fun `should fail when return status is not 2xx`() {
        wireMockServer.stubFor(
            wiremockBuilder
                .withRequestBody(WireMock.equalToJson(forventetRequest))
                .willReturn(WireMock.forbidden())
        )
        val client = DokArkivClient(
            wireMockServer.baseUrl(),
            TokenOppslagStub
        )

        client.opprettJournalpost(nySøknad, pdf) shouldBe
            ClientError(403, "Feil ved journalføring av søknad.").left()
    }

    val wiremockBuilder = WireMock.post(WireMock.urlPathEqualTo(dokArkivPath))
        .withQueryParam("forsoekFerdigstill", WireMock.equalTo("true"))
        .withHeader("Authorization", WireMock.equalTo("Bearer token"))
        .withHeader("Content-Type", WireMock.equalTo("application/json"))
        .withHeader("Accept", WireMock.equalTo("application/json"))
        .withHeader("X-Correlation-ID", WireMock.equalTo("correlationId"))

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
