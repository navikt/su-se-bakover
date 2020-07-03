package no.nav.su.se.bakover.client.pdf

import arrow.core.left
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import io.kotest.matchers.shouldBe
import no.nav.su.meldinger.kafka.soknad.NySøknad
import no.nav.su.meldinger.kafka.soknad.SøknadInnholdTestdataBuilder
import no.nav.su.se.bakover.client.ClientError
import no.nav.su.se.bakover.common.rightValue
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

internal class PdfClientTest {
    val nySøknad = NySøknad(
        sakId = "1",
        søknadId = "1",
        søknad = SøknadInnholdTestdataBuilder.build().toJson(),
        fnr = "12312312312",
        aktørId = "9876543210",
        correlationId = "correlationId"
    )

    @Test
    fun `should generate pdf successfully`() {
        wireMockServer.stubFor(
            wiremockBuilder
                .willReturn(
                    WireMock.ok("pdf-byte-array-here")
                )
        )
        val client = PdfClient(wireMockServer.baseUrl())
        client.genererPdf(nySøknad).rightValue() shouldBe "pdf-byte-array-here".toByteArray()
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

        client.genererPdf(nySøknad) shouldBe ClientError(403, "Kall mot PdfClient feilet").left()
    }

    private val wiremockBuilder = WireMock.post(WireMock.urlPathEqualTo("/api/v1/genpdf/supdfgen/soknad"))
        .withHeader("Content-Type", WireMock.equalTo("application/json"))
        .withHeader("X-Correlation-ID", WireMock.equalTo("correlationId"))
        .withRequestBody(WireMock.equalTo(nySøknad.søknad))

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
