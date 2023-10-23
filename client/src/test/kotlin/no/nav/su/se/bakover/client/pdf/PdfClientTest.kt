package no.nav.su.se.bakover.client.pdf

import arrow.core.left
import arrow.core.right
import com.github.tomakehurst.wiremock.client.WireMock
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.client.WiremockBase
import no.nav.su.se.bakover.client.WiremockBase.Companion.wireMockServer
import no.nav.su.se.bakover.common.domain.PdfA
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.domain.søknad.SøknadPdfInnhold
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.nySkattegrunnlagsPdfInnhold
import no.nav.su.se.bakover.test.søknad.søknadinnholdUføre
import org.junit.jupiter.api.Test
import person.domain.Person
import java.util.UUID
import kotlin.random.Random

internal class PdfClientTest : WiremockBase {

    private val søknadPdfInnhold = SøknadPdfInnhold.create(
        saksnummer = Saksnummer(Random.nextLong(2021, Long.MAX_VALUE)),
        søknadsId = UUID.randomUUID(),
        navn = Person.Navn("Tore", null, "Strømøy"),
        søknadOpprettet = Tidspunkt.EPOCH,
        søknadInnhold = søknadinnholdUføre(),
        clock = fixedClock,
    )
    private val søknadPdfInnholdJson = serialize(søknadPdfInnhold)

    @Test
    fun `should generate pdf successfully`() {
        wireMockServer.stubFor(
            wiremockBuilder
                .willReturn(
                    WireMock.ok("pdf-byte-array-here"),
                ),
        )
        val client = PdfClient(wireMockServer.baseUrl())
        client.genererPdf(søknadPdfInnhold)
            .map { it } shouldBe PdfA("pdf-byte-array-here".toByteArray()).right()
    }

    @Test
    fun `generer pdf for pdfInnhold`() {
        val request = nySkattegrunnlagsPdfInnhold()
        val wiremockBuilder = WireMock.post(WireMock.urlPathEqualTo("/api/v1/genpdf/supdfgen/skattegrunnlag"))
            .withHeader("Content-Type", WireMock.equalTo("application/json"))
            .withHeader("X-Correlation-ID", WireMock.equalTo("correlationId"))
            .withRequestBody(WireMock.equalTo(serialize(request)))

        wireMockServer.stubFor(
            wiremockBuilder
                .willReturn(
                    WireMock.ok("pdf-byte-array-here"),
                ),
        )
        val client = PdfClient(wireMockServer.baseUrl())
        client.genererPdf(request)
            .map { it } shouldBe PdfA("pdf-byte-array-here".toByteArray()).right()
    }

    @Test
    fun `returns KunneIkkeGenererePdf`() {
        wireMockServer.stubFor(
            wiremockBuilder
                .willReturn(
                    WireMock.forbidden(),
                ),
        )
        val client = PdfClient(wireMockServer.baseUrl())

        client.genererPdf(søknadPdfInnhold) shouldBe KunneIkkeGenererePdf.left()
    }

    private val wiremockBuilder = WireMock.post(WireMock.urlPathEqualTo("/api/v1/genpdf/supdfgen/soknad"))
        .withHeader("Content-Type", WireMock.equalTo("application/json"))
        .withHeader("X-Correlation-ID", WireMock.equalTo("correlationId"))
        .withRequestBody(WireMock.equalTo(søknadPdfInnholdJson))
}
