package no.nav.su.se.bakover.client.pdf

import com.github.tomakehurst.wiremock.client.WireMock
import io.kotest.assertions.arrow.either.shouldBeLeft
import io.kotest.assertions.arrow.either.shouldBeRight
import no.nav.su.se.bakover.client.WiremockBase
import no.nav.su.se.bakover.client.WiremockBase.Companion.wireMockServer
import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.domain.SøknadInnholdTestdataBuilder
import no.nav.su.se.bakover.domain.brev.PdfTemplate
import org.junit.jupiter.api.Test

internal class PdfClientTest : WiremockBase {

    private val søknadInnholdJson = objectMapper.writeValueAsString(SøknadInnholdTestdataBuilder.build())

    @Test
    fun `should generate pdf successfully`() {
        wireMockServer.stubFor(
            wiremockBuilder
                .willReturn(
                    WireMock.ok("pdf-byte-array-here")
                )
        )
        val client = PdfClient(wireMockServer.baseUrl())
        client.genererPdf(søknadInnholdJson, PdfTemplate.Søknad)
            .map { String(it) } shouldBeRight String("pdf-byte-array-here".toByteArray())
    }

    @Test
    fun `returns ClientError`() {
        wireMockServer.stubFor(wiremockBuilder.willReturn(WireMock.forbidden()))
        val client = PdfClient(wireMockServer.baseUrl())

        client.genererPdf(søknadInnholdJson, PdfTemplate.Søknad) shouldBeLeft KunneIkkeGenererePdf
    }

    private val wiremockBuilder = WireMock.post(WireMock.urlPathEqualTo("/api/v1/genpdf/supdfgen/soknad"))
        .withHeader("Content-Type", WireMock.equalTo("application/json"))
        .withHeader("X-Correlation-ID", WireMock.equalTo("correlationId"))
        .withRequestBody(WireMock.equalTo(søknadInnholdJson))
}
