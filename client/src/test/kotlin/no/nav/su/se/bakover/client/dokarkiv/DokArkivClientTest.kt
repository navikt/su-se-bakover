package no.nav.su.se.bakover.client.dokarkiv

import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import com.github.tomakehurst.wiremock.client.WireMock
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.client.ClientError
import no.nav.su.se.bakover.client.WiremockBase
import no.nav.su.se.bakover.client.WiremockBase.Companion.wireMockServer
import no.nav.su.se.bakover.client.stubs.pdf.PdfGeneratorStub
import no.nav.su.se.bakover.client.stubs.person.PersonOppslagStub
import no.nav.su.se.bakover.client.stubs.sts.TokenOppslagStub
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.common.startOfDay
import no.nav.su.se.bakover.domain.Person
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.SøknadInnholdTestdataBuilder
import no.nav.su.se.bakover.domain.VedtakInnholdTestdataBuilder
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.søknad.SøknadPdfInnhold
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.ZoneOffset
import java.util.Base64
import java.util.UUID
import kotlin.random.Random

internal class DokArkivClientTest : WiremockBase {

    private val fixedClock: Clock = Clock.fixed(1.januar(2021).startOfDay().instant, ZoneOffset.UTC)

    private val saksnummer: Long = 2021
    private val navn = "Strømøy, Tore Johnas"
    private val søknadInnhold = SøknadInnholdTestdataBuilder.build()
    private val søknadPdfInnhold = SøknadPdfInnhold.create(
        saksnummer = Saksnummer(Random.nextLong(2021, Long.MAX_VALUE)),
        søknadsId = UUID.randomUUID(),
        navn = Person.Navn("Tore", null, "Strømøy"),
        søknadOpprettet = Tidspunkt.EPOCH,
        søknadInnhold = søknadInnhold,
        clock = fixedClock,
    )
    private val vedtakInnhold = VedtakInnholdTestdataBuilder.build()

    private val pdf = PdfGeneratorStub.genererPdf(søknadPdfInnhold).orNull()!!
    private val fnr = søknadInnhold.personopplysninger.fnr
    private val person: Person = PersonOppslagStub.person(fnr).getOrElse {
        throw RuntimeException("fnr fants ikke")
    }

    private val client = DokArkivClient(
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
                      "behandlingstema": "ab0431",
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
                        "fagsakId": "$saksnummer",
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
            .encodeToString(objectMapper.writeValueAsString(søknadInnhold).toByteArray())
        }",
                              "variantformat": "ORIGINAL"
                            }
                          ]
                        }
                      ]
                    }
        """.trimIndent()

    private val forventetVedtaksRequest =
        """
                    {
                      "tittel": "Vedtaksbrev for søknad om supplerende stønad",
                      "journalpostType": "UTGAAENDE",
                      "tema": "SUP",
                      "kanal": null,
                      "behandlingstema": "ab0431",
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
                        "fagsakId": "$saksnummer",
                        "fagsaksystem": "SUPSTONAD",
                        "sakstype": "FAGSAK"
                      },
                      "dokumenter": [
                        {
                          "tittel": "Vedtaksbrev for søknad om supplerende stønad",
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
                saksnummer = Saksnummer(2021),
                person = person,
                søknadInnhold = søknadInnhold,
                pdf = pdf
            )
        ).shouldBe(
            JournalpostId("1").right()
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
                saksnummer = Saksnummer(2021),
                person = person,
                søknadInnhold = søknadInnhold,
                pdf = pdf
            )
        ) shouldBe
            ClientError(403, "Feil ved journalføring").left()
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
                brevInnhold = VedtakInnholdTestdataBuilder.build(),
                person = person,
                pdf = pdf,
                saksnummer = Saksnummer(saksnummer)
            )
        ) shouldBe(
            JournalpostId("1").right()
            )
    }

    private val wiremockBuilder = WireMock.post(WireMock.urlPathEqualTo(dokArkivPath))
        .withQueryParam("forsoekFerdigstill", WireMock.equalTo("true"))
        .withHeader("Authorization", WireMock.equalTo("Bearer token"))
        .withHeader("Content-Type", WireMock.equalTo("application/json"))
        .withHeader("Accept", WireMock.equalTo("application/json"))
        .withHeader("X-Correlation-ID", WireMock.equalTo("correlationId"))
}
