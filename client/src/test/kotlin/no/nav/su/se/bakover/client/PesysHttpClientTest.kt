package no.nav.su.se.bakover.client

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.containing
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import no.nav.su.se.bakover.client.pesys.PesysHttpClient
import no.nav.su.se.bakover.client.pesys.ResponseDto
import no.nav.su.se.bakover.common.auth.AzureAd
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.domain.client.ClientError
import no.nav.su.se.bakover.common.infrastructure.config.ApplicationConfig.ClientsConfig.PesysConfig
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.test.wiremock.startedWireMockServerWithCorrelationId
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.time.LocalDate

val testdata = """{ "resultat" : [ { "fnr" : "22503904369", "perioder" : [ { "netto" : 20983, "fom" : "2025-05-01", "tom" : null, "grunnbelop" : 130160 } ] }, { "fnr" : "01416304056", "perioder" : [ ] }, { "fnr" : "10435046563", "perioder" : [ { "netto" : 47292, "fom" : "2025-05-01", "tom" : null, "grunnbelop" : 130160 } ] }, { "fnr" : "01445407670", "perioder" : [ { "netto" : 32123, "fom" : "2025-05-01", "tom" : null, "grunnbelop" : 124028 } ] }, { "fnr" : "14445014177", "perioder" : [ { "netto" : 39642, "fom" : "2025-05-01", "tom" : null, "grunnbelop" : 130160 } ] }, { "fnr" : "24415045545", "perioder" : [ { "netto" : 47994, "fom" : "2025-05-01", "tom" : null, "grunnbelop" : 130160 } ] } ] }"""

class PesysHttpClientTest {

    val hardkodetFnrs = listOf(
        "22503904369",
        "01416304056",
        "10435046563",
        "01445407670",
        "14445014177",
        "24415045545",
    ).map { Fnr(it) }

    val emptyResponse = """
            {
              "resultat": []
            }
    """.trimIndent()

    private fun mockAzureAd() = mock<AzureAd> {
        on { getSystemToken(any()) } doReturn "token"
    }

    private fun createClient(baseUrl: String, azureAd: AzureAd = mockAzureAd()): PesysHttpClient {
        val config = PesysConfig.createLocalConfig()
        return PesysHttpClient(
            azureAd = azureAd,
            url = "$baseUrl/",
            clientId = config.clientId,
        )
    }

    @Test
    fun `Kan kalle pesys med fnr liste i dag`() {
        startedWireMockServerWithCorrelationId {
            stubFor(
                post(urlPathEqualTo("/alderspensjon/vedtak/iverksatt"))
                    .withQueryParam("fom", equalTo(LocalDate.now().toString()))
                    .withHeader("Content-Type", containing("application/json"))
                    .willReturn(
                        aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody(testdata),
                    ),
            )

            val result = createClient(baseUrl()).hentVedtakForPersonPaaDatoAlder(hardkodetFnrs, LocalDate.now())

            result.shouldBeRight(deserialize<ResponseDto>(testdata))
        }
    }

    @Test
    fun `henting feiler med tom fnr-liste`() {
        startedWireMockServerWithCorrelationId {
            // Stub for 500 når listen er tom
            val bodymsg = "Mangler fnrliste"
            val datoFom = LocalDate.now()
            stubFor(
                post(urlPathEqualTo("/alderspensjon/vedtak/iverksatt"))
                    .withQueryParam("fom", equalTo(datoFom.toString()))
                    .withHeader("Content-Type", containing("application/json"))
                    .willReturn(
                        aResponse()
                            .withStatus(500)
                            .withHeader("Content-Type", "application/json")
                            .withBody(bodymsg),
                    ),
            )

            val result = createClient(baseUrl()).hentVedtakForPersonPaaDatoAlder(emptyList(), datoFom)

            result.shouldBeLeft().let { clientError: ClientError ->
                clientError.httpStatus shouldBe 500
                clientError.message shouldContain bodymsg
            }
        }
    }

    @Test
    fun `Feiler hvis dato er mer enn 12 mnd tilbake`() {
        startedWireMockServerWithCorrelationId {
            val fomDato = LocalDate.now().minusYears(13)
            val body = "Fom dato kan ikke være eldre enn 12 mnd tilbake i tid"
            stubFor(
                post(urlPathEqualTo("/alderspensjon/vedtak/iverksatt"))
                    .withQueryParam("fom", equalTo(fomDato.toString()))
                    .withHeader("Content-Type", containing("application/json"))
                    .willReturn(
                        aResponse()
                            .withStatus(500)
                            .withHeader("Content-Type", "application/json;")
                            .withBody(body),
                    ),
            )
            val result = createClient(baseUrl()).hentVedtakForPersonPaaDatoAlder(hardkodetFnrs, fomDato)

            result.shouldBeLeft().let { clientError ->
                clientError.httpStatus shouldBe 500
                clientError.message shouldContain body
            }
        }
    }

    @Test
    fun `feiler hvis fnr-liste er større enn 50`() {
        startedWireMockServerWithCorrelationId {
            val fomDato = LocalDate.now()
            val body = "Maks 50 fnr i fnrliste"
            stubFor(
                post(urlPathEqualTo("/alderspensjon/vedtak/iverksatt"))
                    .withQueryParam("fom", equalTo(fomDato.toString()))
                    .withHeader("Content-Type", containing("application/json"))
                    .willReturn(
                        aResponse()
                            .withStatus(500)
                            .withHeader("Content-Type", "application/json;")
                            .withBody(body),
                    ),
            )

            val fnrListe = List(51) {
                hardkodetFnrs[it % hardkodetFnrs.size]
            }
            val result = createClient(baseUrl()).hentVedtakForPersonPaaDatoAlder(fnrListe, fomDato)

            result.shouldBeLeft().let { error ->
                error.httpStatus shouldBe 500
                error.message shouldContain body
            }
        }
    }
}
