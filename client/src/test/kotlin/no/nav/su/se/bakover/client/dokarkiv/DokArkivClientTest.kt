package no.nav.su.se.bakover.client.dokarkiv

import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import com.github.tomakehurst.wiremock.client.WireMock
import dokument.domain.Dokument
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.client.ClientError
import no.nav.su.se.bakover.client.WiremockBase
import no.nav.su.se.bakover.client.WiremockBase.Companion.wireMockServer
import no.nav.su.se.bakover.client.stubs.pdf.PdfGeneratorStub
import no.nav.su.se.bakover.client.stubs.person.PersonOppslagStub
import no.nav.su.se.bakover.client.stubs.sts.TokenOppslagStub
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.journal.JournalpostId
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.domain.journalpost.JournalpostForSakCommand
import no.nav.su.se.bakover.domain.sak.Sakstype
import no.nav.su.se.bakover.domain.søknad.SøknadPdfInnhold
import no.nav.su.se.bakover.test.brev.pdfInnholdInnvilgetVedtak
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.sakId
import no.nav.su.se.bakover.test.søknad.søknadinnholdUføre
import org.junit.jupiter.api.Test
import person.domain.Person
import java.util.Base64
import java.util.UUID
import kotlin.random.Random

internal class DokArkivClientTest : WiremockBase {

    private val saksnummer: Long = 2021
    private val navn = "Strømøy, Tore Johnas"
    private val søknadInnhold = søknadinnholdUføre()
    private val søknadPdfInnhold = SøknadPdfInnhold.create(
        saksnummer = Saksnummer(Random.nextLong(2021, Long.MAX_VALUE)),
        søknadsId = UUID.randomUUID(),
        navn = Person.Navn("Tore", null, "Strømøy"),
        søknadOpprettet = Tidspunkt.EPOCH,
        søknadInnhold = søknadInnhold,
        clock = fixedClock,
    )
    private val vedtaksDokument = Dokument.MedMetadata.Vedtak(
        utenMetadata = Dokument.UtenMetadata.Vedtak(
            id = UUID.randomUUID(),
            opprettet = fixedTidspunkt,
            tittel = "Vedtaksbrev for søknad om supplerende stønad",
            generertDokument = PdfGeneratorStub.genererPdf(pdfInnholdInnvilgetVedtak()).getOrFail(),
            generertDokumentJson = serialize(pdfInnholdInnvilgetVedtak()),
        ),
        metadata = Dokument.Metadata(sakId = sakId),
    )

    private val pdf = PdfGeneratorStub.genererPdf(søknadPdfInnhold).getOrFail()
    private val fnr = søknadInnhold.personopplysninger.fnr
    private val person: Person = PersonOppslagStub.person(fnr).getOrElse {
        throw RuntimeException("fnr fants ikke")
    }

    private val client = DokArkivClient(
        wireMockServer.baseUrl(),
        TokenOppslagStub,
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
                          "brevkode": "XX.YY-ZZ",
                          "dokumentvarianter": [
                            {
                              "filtype": "PDFA",
                              "fysiskDokument": "${Base64.getEncoder().encodeToString(pdf.getContent())}",
                              "variantformat": "ARKIV"
                            },
                            {
                              "filtype": "JSON",
                              "fysiskDokument": "${
            Base64.getEncoder()
                .encodeToString(serialize(søknadInnhold).toByteArray())
        }",
                              "variantformat": "ORIGINAL"
                            }
                          ]
                        }
                      ],
                        "datoDokument": "2021-01-01T01:02:03.456789Z"
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
                          "brevkode": "XX.YY-ZZ",
                          "dokumentvarianter": [
                            {
                              "filtype": "PDFA",
                              "fysiskDokument": "${Base64.getEncoder().encodeToString(vedtaksDokument.generertDokument.getContent())}",
                              "variantformat": "ARKIV"
                            },
                            {
                              "filtype": "JSON",
                              "fysiskDokument": "${
            Base64.getEncoder().encodeToString(vedtaksDokument.generertDokumentJson.toByteArray())
        }",
                              "variantformat": "ORIGINAL"
                            }
                          ]
                        }
                      ],
                      "datoDokument": "2021-01-01T01:02:03.456789Z"
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
                        """.trimIndent(),
                    ),
                ),
        )
        client.opprettJournalpost(
            JournalpostForSakCommand.Søknadspost(
                saksnummer = Saksnummer(2021),
                søknadInnhold = søknadInnhold,
                pdf = pdf,
                sakstype = Sakstype.UFØRE,
                datoDokument = fixedTidspunkt,
                fnr = person.ident.fnr,
                navn = person.navn,
            ),
        ).shouldBe(
            JournalpostId("1").right(),
        )
    }

    @Test
    fun `should fail when return status is not 2xx`() {
        wireMockServer.stubFor(
            wiremockBuilder
                .withRequestBody(WireMock.equalToJson(forventetSøknadsRequest))
                .willReturn(WireMock.forbidden()),
        )

        client.opprettJournalpost(
            JournalpostForSakCommand.Søknadspost(
                saksnummer = Saksnummer(2021),
                søknadInnhold = søknadInnhold,
                pdf = pdf,
                sakstype = Sakstype.UFØRE,
                datoDokument = fixedTidspunkt,
                fnr = person.ident.fnr,
                navn = person.navn,
            ),
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
                          ],
                          "datoDokument": "2021-01-01T01:02:03.456789Z"
                        }
                        """.trimIndent(),
                    ),
                ),
        )

        client.opprettJournalpost(
            JournalpostForSakCommand.Brev(
                saksnummer = Saksnummer(2021),
                sakstype = Sakstype.UFØRE,
                fnr = person.ident.fnr,
                navn = person.navn,
                dokument = vedtaksDokument,
            ),
        ) shouldBe (
            JournalpostId("1").right()
            )
    }

    private val wiremockBuilder = WireMock.post(WireMock.urlPathEqualTo(DOK_ARKIV_PATH))
        .withQueryParam("forsoekFerdigstill", WireMock.equalTo("true"))
        .withHeader("Authorization", WireMock.equalTo("Bearer token"))
        .withHeader("Content-Type", WireMock.equalTo("application/json"))
        .withHeader("Accept", WireMock.equalTo("application/json"))
        .withHeader("X-Correlation-ID", WireMock.equalTo("correlationId"))
}
