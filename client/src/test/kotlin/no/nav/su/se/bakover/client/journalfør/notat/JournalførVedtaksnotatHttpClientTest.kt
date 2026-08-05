package no.nav.su.se.bakover.client.journalfør.notat

import com.github.tomakehurst.wiremock.client.WireMock
import io.kotest.assertions.arrow.core.shouldBeRight
import no.nav.su.se.bakover.client.stubs.azure.AzureClientStub
import no.nav.su.se.bakover.common.domain.PdfA
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.infrastructure.config.ApplicationConfig
import no.nav.su.se.bakover.dokument.infrastructure.client.journalføring.DOK_ARKIV_PATH
import no.nav.su.se.bakover.dokument.infrastructure.client.journalføring.JournalførHttpClient
import no.nav.su.se.bakover.domain.notat.JournalførVedtaksnotatCommand
import no.nav.su.se.bakover.domain.notat.JournalførbartVedlegg
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.fnr
import no.nav.su.se.bakover.test.saksnummer
import no.nav.su.se.bakover.test.wiremock.startedWireMockServerWithCorrelationId
import org.junit.jupiter.api.Test
import java.util.Base64
import java.util.UUID

internal class JournalførVedtaksnotatHttpClientTest {

    private val notatPdfBytes = "dette er notat-pdf".toByteArray()
    private val vedleggPdfBytes = "dette er vedlegg-pdf".toByteArray()

    @Test
    fun `hvert dokument sendes med en arkivvariant (PDFA) og vedleggets pdf-bytes journalfores`() {
        startedWireMockServerWithCorrelationId {
            stubFor(
                WireMock.post(WireMock.urlPathEqualTo(DOK_ARKIV_PATH))
                    .withQueryParam("forsoekFerdigstill", WireMock.equalTo("true"))
                    // Notat-teksten (hoveddokument) må ha en ARKIV-variant i tillegg til ORIGINAL.
                    .withRequestBody(
                        WireMock.matchingJsonPath(
                            "$.dokumenter[0].dokumentvarianter[?(@.variantformat == 'ARKIV' && @.filtype == 'PDFA')]",
                        ),
                    )
                    .withRequestBody(
                        WireMock.matchingJsonPath("$.dokumenter[0].dokumentvarianter[?(@.variantformat == 'ORIGINAL')]"),
                    )
                    // Vedlegget skal være konvertert til PDF og sendes som ARKIV.
                    .withRequestBody(
                        WireMock.matchingJsonPath(
                            "$.dokumenter[1].dokumentvarianter[?(@.variantformat == 'ARKIV' && @.filtype == 'PDFA')]",
                        ),
                    )
                    // De faktiske (konverterte) bytene skal journalføres.
                    .withRequestBody(
                        WireMock.matchingJsonPath(
                            "$.dokumenter[1].dokumentvarianter[0].fysiskDokument",
                            WireMock.equalTo(Base64.getEncoder().encodeToString(vedleggPdfBytes)),
                        ),
                    )
                    .willReturn(
                        WireMock.okJson(
                            """{"journalpostId":"1","journalpostferdigstilt":true,"dokumenter":[{"dokumentInfoId":"1"}]}""",
                        ),
                    ),
            )

            val client = createJournalførVedtaksnotatHttpClient(
                JournalførHttpClient(
                    dokArkivConfig = ApplicationConfig.ClientsConfig.DokArkivConfig(
                        url = baseUrl(),
                        clientId = "clientId",
                    ),
                    azureAd = AzureClientStub,
                ),
            )

            client.journalførVedtaksnotat(command()).shouldBeRight()
        }
    }

    private fun command() = JournalførVedtaksnotatCommand(
        sakstype = Sakstype.UFØRE,
        saksnummer = saksnummer,
        fnr = fnr,
        notatId = UUID.randomUUID(),
        tittel = "Vedtaksnotat",
        notat = "Saksbehandlers notat",
        attestantNotat = "Attestants notat",
        notatPdf = PdfA(notatPdfBytes),
        vedlegg = listOf(
            JournalførbartVedlegg(filnavn = "bilde.pdf", pdf = PdfA(vedleggPdfBytes)),
        ),
        datoDokument = fixedTidspunkt,
    )
}
