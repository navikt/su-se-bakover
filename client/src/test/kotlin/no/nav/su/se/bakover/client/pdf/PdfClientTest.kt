package no.nav.su.se.bakover.client.pdf

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import io.kotest.assertions.arrow.either.shouldBeLeft
import io.kotest.assertions.arrow.either.shouldBeRight
import no.nav.su.meldinger.kafka.soknad.SøknadInnholdTestdataBuilder
import no.nav.su.se.bakover.client.ClientError
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.slf4j.MDC

internal class PdfClientTest {

    @Test
    fun `should generate pdf successfully`() {
        wireMockServer.stubFor(
            wiremockBuilder
                .willReturn(
                    WireMock.ok("pdf-byte-array-here")
                )
        )
        val client = PdfClient(wireMockServer.baseUrl())
        client.genererPdf(SøknadInnholdTestdataBuilder.build()) shouldBeRight "pdf-byte-array-here".toByteArray()
    }

    @Test
    fun `returns ClientError`() {
        wireMockServer.stubFor(
            wiremockBuilder
                .willReturn(
                    WireMock.forbidden()
                )
        )
        val client = PdfClient(wireMockServer.baseUrl())

        client.genererPdf(SøknadInnholdTestdataBuilder.build()) shouldBeLeft ClientError(
            403,
            "Kall mot PdfClient feilet"
        )
    }

    private val wiremockBuilder = WireMock.post(WireMock.urlPathEqualTo("/api/v1/genpdf/supdfgen/soknad"))
        .withHeader("Content-Type", WireMock.equalTo("application/json"))
        .withHeader("X-Correlation-ID", WireMock.equalTo("correlationId"))
        .withRequestBody(WireMock.equalTo(SøknadInnholdTestdataBuilder.build().toJson()))

    companion object {
        val wireMockServer: WireMockServer = WireMockServer(WireMockConfiguration.options().dynamicPort())

        @BeforeAll
        @JvmStatic
        fun start() {
            wireMockServer.start()
            MDC.put("X-Correlation-ID", "correlationId")
        }

        @AfterAll
        @JvmStatic
        fun stop() {
            wireMockServer.stop()
        }
    }
}
