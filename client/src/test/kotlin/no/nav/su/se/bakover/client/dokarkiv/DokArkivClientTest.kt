package no.nav.su.se.bakover.client.dokarkiv

import arrow.core.left
import arrow.core.orNull
import arrow.core.right
import com.github.tomakehurst.wiremock.client.WireMock
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.client.ClientError
import no.nav.su.se.bakover.client.WiremockBase
import no.nav.su.se.bakover.client.WiremockBase.Companion.wireMockServer
import no.nav.su.se.bakover.client.person.PdlData
import no.nav.su.se.bakover.client.stubs.pdf.PdfGeneratorStub
import no.nav.su.se.bakover.client.stubs.person.PersonOppslagStub
import no.nav.su.se.bakover.client.stubs.sts.TokenOppslagStub
import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.domain.SøknadInnholdTestdataBuilder
import org.junit.jupiter.api.Test
import java.util.Base64

internal class DokArkivClientTest : WiremockBase {

    private val sakId = 1
    private val søknadInnhold = SøknadInnholdTestdataBuilder.build()
    private val søknadInnholdJson = objectMapper.writeValueAsString(søknadInnhold)

    private val pdf = PdfGeneratorStub.genererPdf(søknadInnhold).orNull()!!
    private val fnr = søknadInnhold.personopplysninger.fnr
    private val person: PdlData = PersonOppslagStub.person(fnr).orNull()!!

    private val forventetRequest =
        """
        {
          "tittel": "Søknad om supplerende stønad for uføre flyktninger",
          "journalpostType": "INNGAAENDE",
          "tema": "SUP",
          "kanal": "INNSENDT_NAV_ANSATT",
          "behandlingstema": "ab0268",
          "journalfoerendeEnhet": "9999",
          "avsenderMottaker": {
            "id": "$fnr",
            "idType": "FNR",
            "navn": "Strømøy, Tore Johnas"
          },
          "bruker": {
            "id": "$fnr",
            "idType": "FNR"
          },
          "sak": {
            "fagsakId": "$sakId",
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
                  "fysiskDokument": "${Base64.getEncoder().encodeToString(søknadInnholdJson.toByteArray())}",
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

        client.opprettJournalpost(søknadInnhold, person, pdf, "1").shouldBe(
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

        client.opprettJournalpost(søknadInnhold, person, pdf, "1") shouldBe
            ClientError(403, "Feil ved journalføring av søknad.").left()
    }

    val wiremockBuilder = WireMock.post(WireMock.urlPathEqualTo(dokArkivPath))
        .withQueryParam("forsoekFerdigstill", WireMock.equalTo("true"))
        .withHeader("Authorization", WireMock.equalTo("Bearer token"))
        .withHeader("Content-Type", WireMock.equalTo("application/json"))
        .withHeader("Accept", WireMock.equalTo("application/json"))
        .withHeader("X-Correlation-ID", WireMock.equalTo("correlationId"))
}
