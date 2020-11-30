package no.nav.su.se.bakover.client.pdf

import com.github.tomakehurst.wiremock.client.WireMock
import io.kotest.assertions.arrow.either.shouldBeLeft
import io.kotest.assertions.arrow.either.shouldBeRight
import no.nav.su.se.bakover.client.ClientError
import no.nav.su.se.bakover.client.WiremockBase
import no.nav.su.se.bakover.client.WiremockBase.Companion.wireMockServer
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.ddMMyyyy
import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.common.zoneIdOslo
import no.nav.su.se.bakover.domain.Person
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.SøknadInnholdTestdataBuilder
import no.nav.su.se.bakover.domain.søknad.SøknadPdfInnhold
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.random.Random

internal class PdfClientTest : WiremockBase {

    private val søknadPdfInnhold = SøknadPdfInnhold(
        saksnummer = Saksnummer(Random.nextLong()),
        søknadsId = UUID.randomUUID(),
        navn = Person.Navn("Tore", null, "Strømøy"),
        søknadOpprettet = Tidspunkt.EPOCH.toLocalDate(zoneIdOslo).ddMMyyyy(),
        søknadInnhold = SøknadInnholdTestdataBuilder.build()
    )
    private val søknadPdfInnholdJson = objectMapper.writeValueAsString(søknadPdfInnhold)

    @Test
    fun `should generate pdf successfully`() {
        wireMockServer.stubFor(
            wiremockBuilder
                .willReturn(
                    WireMock.ok("pdf-byte-array-here")
                )
        )
        val client = PdfClient(wireMockServer.baseUrl())
        client.genererPdf(søknadPdfInnhold).map { String(it) } shouldBeRight String("pdf-byte-array-here".toByteArray())
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

        client.genererPdf(søknadPdfInnhold) shouldBeLeft ClientError(
            403,
            "Kall mot PdfClient feilet"
        )
    }

    private val wiremockBuilder = WireMock.post(WireMock.urlPathEqualTo("/api/v1/genpdf/supdfgen/soknad"))
        .withHeader("Content-Type", WireMock.equalTo("application/json"))
        .withHeader("X-Correlation-ID", WireMock.equalTo("correlationId"))
        .withRequestBody(WireMock.equalTo(søknadPdfInnholdJson))
}
