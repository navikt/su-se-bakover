package no.nav.su.se.bakover.dokument.infrastructure.database.journalføring

import arrow.core.left
import arrow.core.right
import com.github.tomakehurst.wiremock.client.WireMock
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.domain.client.ClientError
import no.nav.su.se.bakover.common.infrastructure.auth.TokenOppslagStub
import no.nav.su.se.bakover.common.journal.JournalpostId
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.wiremock.startedWireMockServerWithCorrelationId
import org.junit.jupiter.api.Test

internal class JournalførHttpClientTest {

    @Test
    fun `happy case`() {
        startedWireMockServerWithCorrelationId {
            val expectedRequest =
                """
                    {
                      "tittel": "tittel",
                      "journalpostType": "INNGAAENDE",
                      "tema": "tema",
                      "kanal": "kanal",
                      "behandlingstema": "behandlingstema",
                      "journalfoerendeEnhet": "journalfoerendeEnhet",
                      "avsenderMottaker": {
                        "id": "id",
                        "idType": "FNR"
                      },
                      "bruker": {
                        "id": "id",
                        "idType": "idType"
                      },
                      "sak": {
                        "fagsakId": "fagsakId",
                        "fagsaksystem": "fagsaksystem",
                        "sakstype": "sakstype"
                      },
                      "dokumenter": [
                        {
                          "tittel": "tittel",
                          "brevkode": "brevkode",
                          "dokumentvarianter": [
                            {
                              "filtype": "PDFA",
                              "fysiskDokument": "fysiskDokumentPdf",
                              "variantformat": "ARKIV"
                            },
                            {
                              "filtype": "JSON",
                              "fysiskDokument": "fysiskDokumentJson",
                              "variantformat": "ORIGINAL"
                            }
                          ]
                        }
                      ],
                        "datoDokument": "2021-01-01T01:02:03.456789Z",
                        "eksternReferanseId": "eksternReferanseId"
                    }
                """.trimIndent()
            stubFor(
                wiremockBuilder
                    .withRequestBody(WireMock.equalToJson(expectedRequest))
                    .willReturn(
                        WireMock.okJson(
                            """
                        {
                          "journalpostId": "1",
                          "journalpostferdigstilt": true,
                          "dokumenter": [
                            {
                              "dokumentInfoId": "12345",
                              "tittel": "tittel"
                            }
                          ]
                        }
                            """.trimIndent(),
                        ),
                    ),
            )
            val client = no.nav.su.se.bakover.dokument.infrastructure.database.journalføring.JournalførHttpClient(
                baseUrl = baseUrl(),
                tokenOppslag = TokenOppslagStub,
            )

            client.opprettJournalpost(
                request,
            ).shouldBe(
                JournalpostId("1").right(),
            )
        }
    }

    @Test
    fun `should fail when return status is not 2xx`() {
        startedWireMockServerWithCorrelationId {
            stubFor(
                wiremockBuilder
                    .willReturn(WireMock.forbidden()),
            )
            val client = no.nav.su.se.bakover.dokument.infrastructure.database.journalføring.JournalførHttpClient(
                baseUrl = baseUrl(),
                tokenOppslag = TokenOppslagStub,
            )
            client.opprettJournalpost(request) shouldBe
                ClientError(403, "Feil ved journalføring").left()
        }
    }

    private val request = JournalførJsonRequest(
        tittel = "tittel",
        journalpostType = JournalPostType.INNGAAENDE,
        tema = "tema",
        kanal = "kanal",
        behandlingstema = "behandlingstema",
        journalfoerendeEnhet = "journalfoerendeEnhet",
        avsenderMottaker = AvsenderMottaker(id = "id"),
        bruker = Bruker(id = "id", idType = "idType"),
        sak = Fagsak(
            fagsakId = "fagsakId",
            fagsaksystem = "fagsaksystem",
            sakstype = "sakstype",
        ),
        dokumenter = listOf(
            JournalpostDokument(
                tittel = "tittel",
                brevkode = "brevkode",
                dokumentvarianter = listOf(
                    DokumentVariant.ArkivPDF(
                        fysiskDokument = "fysiskDokumentPdf",
                    ),
                    DokumentVariant.OriginalJson(
                        fysiskDokument = "fysiskDokumentJson",
                    ),
                ),
            ),
        ),
        datoDokument = fixedTidspunkt,
        eksternReferanseId = "eksternReferanseId",
    )

    private val wiremockBuilder = WireMock.post(WireMock.urlPathEqualTo(no.nav.su.se.bakover.dokument.infrastructure.database.journalføring.DOK_ARKIV_PATH))
        .withQueryParam("forsoekFerdigstill", WireMock.equalTo("true"))
        .withHeader("Authorization", WireMock.equalTo("Bearer token"))
        .withHeader("Content-Type", WireMock.equalTo("application/json"))
        .withHeader("Accept", WireMock.equalTo("application/json"))
        .withHeader("X-Correlation-ID", WireMock.equalTo("correlationId"))
}
