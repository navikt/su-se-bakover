package no.nav.su.se.bakover.client.dokarkiv

import arrow.core.getOrElse
import arrow.core.left
import arrow.core.orNull
import arrow.core.right
import com.github.tomakehurst.wiremock.client.WireMock
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.client.ClientError
import no.nav.su.se.bakover.client.WiremockBase
import no.nav.su.se.bakover.client.WiremockBase.Companion.wireMockServer
import no.nav.su.se.bakover.client.stubs.pdf.PdfGeneratorStub
import no.nav.su.se.bakover.client.stubs.person.PersonOppslagStub
import no.nav.su.se.bakover.client.stubs.sts.TokenOppslagStub
import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.domain.Person
import no.nav.su.se.bakover.domain.SøknadInnholdTestdataBuilder
import no.nav.su.se.bakover.domain.VedtakInnholdTestdataBuilder
import org.junit.jupiter.api.Test
import java.util.Base64

internal class DokArkivClientTest : WiremockBase {

    private val sakId = "1"
    private val navn = "Strømøy, Tore Johnas"
    private val søknadInnhold = SøknadInnholdTestdataBuilder.build()
    private val vedtakInnhold = VedtakInnholdTestdataBuilder.build()

    private val pdf = PdfGeneratorStub.genererPdf(søknadInnhold).orNull()!!
    private val fnr = søknadInnhold.personopplysninger.fnr
    private val person: Person = PersonOppslagStub.person(fnr).getOrElse {
        throw RuntimeException("fnr fants ikke")
    }

    val client = DokArkivClient(
        wireMockServer.baseUrl(),
        TokenOppslagStub
    )

    private val forventetSøknadsRequest =
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
                        "navn": "$navn"
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
                          "dokumentKategori": "SOK",
                          "brevkode": "XX.YY-ZZ",
                          "dokumentvarianter": [
                            {
                              "filtype": "PDFA",
                              "fysiskDokument": "${Base64.getEncoder().encodeToString(pdf)}",
                              "variantformat": "ARKIV"
                            },
                            {
                              "filtype": "JSON",
                              "fysiskDokument": "${
        Base64.getEncoder()
            .encodeToString(objectMapper.writeValueAsString(søknadInnhold).toByteArray())}",
                              "variantformat": "ORIGINAL"
                            }
                          ]
                        }
                      ]
                    }
        """.trimIndent()

    val forventetVedtaksRequest =
        """
                    {
                      "tittel": "Vedtaksbrev for soknad om supplerende stønad",
                      "journalpostType": "UTGAAENDE",
                      "tema": "SUP",
                      "kanal": null,
                      "behandlingstema": "ab0268",
                      "journalfoerendeEnhet": "4815",
                      "avsenderMottaker": {
                        "id": "$fnr",
                        "idType": "FNR",
                        "navn": "$navn"
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
                          "tittel": "Vedtaksbrev for soknad om supplerende stønad",
                          "dokumentKategori": "VB",
                          "brevkode": "XX.YY-ZZ",
                          "dokumentvarianter": [
                            {
                              "filtype": "PDFA",
                              "fysiskDokument": "${Base64.getEncoder().encodeToString(pdf)}",
                              "variantformat": "ARKIV"
                            },
                            {
                              "filtype": "JSON",
                              "fysiskDokument": "${
        Base64.getEncoder()
            .encodeToString(objectMapper.writeValueAsString(vedtakInnhold).toByteArray())}",
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
                .withRequestBody(WireMock.equalToJson(forventetSøknadsRequest))
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
        client.opprettJournalpost(
            Journalpost.Søknadspost(
                sakId = "1",
                person = person,
                søknadInnhold = søknadInnhold,
                pdf = pdf
            )
        ).shouldBe(
            "1".right()
        )
    }

    @Test
    fun `should fail when return status is not 2xx`() {
        wireMockServer.stubFor(
            wiremockBuilder
                .withRequestBody(WireMock.equalToJson(forventetSøknadsRequest))
                .willReturn(WireMock.forbidden())
        )

        client.opprettJournalpost(
            Journalpost.Søknadspost(
                sakId = "1",
                person = person,
                søknadInnhold = søknadInnhold,
                pdf = pdf
            )
        ) shouldBe
            ClientError(403, "Feil ved journalføring av søknad.").left()
    }

    @Test
    fun `should send vedtaks pdf to journal`() {
        wireMockServer.stubFor(
            wiremockBuilder
                .withRequestBody(WireMock.equalToJson(forventetVedtaksRequest))

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

        client.opprettJournalpost(
            Journalpost.Vedtakspost(
                vedtakInnhold = VedtakInnholdTestdataBuilder.build(),
                person = person,
                pdf = pdf,
                sakId = sakId
            )
        ) shouldBe(
            "1".right()
            )
    }

    val wiremockBuilder = WireMock.post(WireMock.urlPathEqualTo(dokArkivPath))
        .withQueryParam("forsoekFerdigstill", WireMock.equalTo("true"))
        .withHeader("Authorization", WireMock.equalTo("Bearer token"))
        .withHeader("Content-Type", WireMock.equalTo("application/json"))
        .withHeader("Accept", WireMock.equalTo("application/json"))
        .withHeader("X-Correlation-ID", WireMock.equalTo("correlationId"))
}
