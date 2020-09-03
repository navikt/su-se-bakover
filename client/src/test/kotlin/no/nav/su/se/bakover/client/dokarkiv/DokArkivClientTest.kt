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
import no.nav.su.se.bakover.domain.Person
import no.nav.su.se.bakover.domain.SøknadInnholdTestdataBuilder
import org.junit.jupiter.api.Test

internal class DokArkivClientTest : WiremockBase {

    private val sakId = "1"
    private val navn = "Strømøy, Tore Johnas"
    private val søknadInnhold = SøknadInnholdTestdataBuilder.build()

    private val pdf = PdfGeneratorStub.genererPdf(søknadInnhold).orNull()!!
    private val fnr = søknadInnhold.personopplysninger.fnr
    private val person: Person = PersonOppslagStub.person(fnr).getOrElse {
        throw RuntimeException("fnr fants ikke")
    }

    val client = DokArkivClient(
        wireMockServer.baseUrl(),
        TokenOppslagStub
    )

    private val forventetRequest = client.byggSøknadspost(fnr, navn, søknadInnhold, sakId, pdf)

    @Test
    fun `should send pdf to journal`() {
        wireMockServer.stubFor(
            wiremockBuilder
                .withRequestBody(WireMock.equalToJson(forventetRequest))
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

        client.opprettJournalpost(søknadInnhold, person, pdf, "1").shouldBe(
            "1".right()
        )
    }

    @Test
    fun `should fail when return status is not 2xx`() {
        wireMockServer.stubFor(
            wiremockBuilder
                .withRequestBody(WireMock.equalToJson(forventetRequest))
                .willReturn(WireMock.forbidden())
        )

        client.opprettJournalpost(søknadInnhold, person, pdf, "1") shouldBe
            ClientError(403, "Feil ved journalføring av søknad.").left()
    }

    val wiremockBuilder = WireMock.post(WireMock.urlPathEqualTo(dokArkivPath))
        .withQueryParam("forsoekFerdigstill", WireMock.equalTo("true"))
        .withHeader("Authorization", WireMock.equalTo("Bearer token"))
        .withHeader("Content-Type", WireMock.equalTo("application/json"))
        .withHeader("Accept", WireMock.equalTo("application/json"))
        .withHeader("X-Correlation-ID", WireMock.equalTo("correlationId"))
}
