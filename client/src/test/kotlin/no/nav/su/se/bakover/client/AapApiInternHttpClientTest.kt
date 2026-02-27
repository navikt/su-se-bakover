package no.nav.su.se.bakover.client

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.containing
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.equalToJson
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import no.nav.su.se.bakover.client.aap.AapApiInternHttpClient
import no.nav.su.se.bakover.client.aap.MaksimumPeriodeDto
import no.nav.su.se.bakover.client.aap.MaksimumRequestDto
import no.nav.su.se.bakover.client.aap.MaksimumResponseDto
import no.nav.su.se.bakover.client.aap.MaksimumVedtakDto
import no.nav.su.se.bakover.common.CORRELATION_ID_HEADER
import no.nav.su.se.bakover.common.auth.AzureAd
import no.nav.su.se.bakover.common.domain.client.ClientError
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.test.wiremock.startedWireMockServerWithCorrelationId
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.time.LocalDate

class AapApiInternHttpClientTest {
    companion object {
        private const val NAV_CALL_ID_HEADER = "nav-callid"
    }

    private fun mockAzureAd() = mock<AzureAd> {
        on { getSystemToken(any()) } doReturn "token"
    }

    private fun createClient(baseUrl: String, azureAd: AzureAd = mockAzureAd()): AapApiInternHttpClient {
        return AapApiInternHttpClient(
            azureAd = azureAd,
            url = "$baseUrl/",
            clientId = "api://dev-gcp.aap.api-intern",
        )
    }

    @Test
    fun `kan hente maksimum for en person`() {
        startedWireMockServerWithCorrelationId {
            val fraOgMedDato = LocalDate.parse("2025-04-01")
            val tilOgMedDato = LocalDate.parse("2025-04-30")
            val fnr = Fnr("22503904369")

            val expectedRequest = serialize(
                MaksimumRequestDto(
                    fraOgMedDato = fraOgMedDato,
                    personidentifikator = fnr.toString(),
                    tilOgMedDato = tilOgMedDato,
                ),
            )
            val expectedResponse = MaksimumResponseDto(
                vedtak = listOf(
                    MaksimumVedtakDto(
                        barnMedStonad = 0,
                        barnetillegg = 0,
                        beregningsgrunnlag = 0,
                        dagsats = 0,
                        dagsatsEtterUforeReduksjon = 0,
                        kildesystem = "ARENA",
                        opphorsAarsak = "string",
                        periode = MaksimumPeriodeDto(
                            fraOgMedDato = LocalDate.parse("2025-04-01"),
                            tilOgMedDato = LocalDate.parse("2025-04-01"),
                        ),
                        rettighetsType = "string",
                        saksnummer = "string",
                        samordningsId = "string",
                        status = "string",
                        vedtakId = "string",
                        vedtaksTypeKode = "string",
                        vedtaksTypeNavn = "string",
                        vedtaksdato = LocalDate.parse("2025-04-01"),
                    ),
                ),
            )

            stubFor(
                post(urlPathEqualTo("/maksimum"))
                    .withHeader(HttpHeaders.ContentType, containing(ContentType.Application.Json.toString()))
                    .withHeader(HttpHeaders.Accept, containing(ContentType.Application.Json.toString()))
                    .withHeader(NAV_CALL_ID_HEADER, equalTo("correlationId"))
                    .withHeader(CORRELATION_ID_HEADER, equalTo("correlationId"))
                    .withRequestBody(equalToJson(expectedRequest))
                    .willReturn(
                        aResponse()
                            .withStatus(200)
                            .withHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                            .withBody(
                                """
                                {
                                  "vedtak": [
                                    {
                                      "barnMedStonad": 0,
                                      "barnetillegg": 0,
                                      "beregningsgrunnlag": 0,
                                      "dagsats": 0,
                                      "dagsatsEtterUføreReduksjon": 0,
                                      "kildesystem": "ARENA",
                                      "opphorsAarsak": "string",
                                      "periode": {
                                        "fraOgMedDato": "2025-04-01",
                                        "tilOgMedDato": "2025-04-01"
                                      },
                                      "rettighetsType": "string",
                                      "saksnummer": "string",
                                      "samordningsId": "string",
                                      "status": "string",
                                      "vedtakId": "string",
                                      "vedtaksTypeKode": "string",
                                      "vedtaksTypeNavn": "string",
                                      "vedtaksdato": "2025-04-01"
                                    }
                                  ]
                                }
                                """.trimIndent(),
                            ),
                    ),
            )

            val result = createClient(baseUrl()).hentMaksimum(fnr, fraOgMedDato, tilOgMedDato)

            result.shouldBeRight(expectedResponse)
        }
    }

    @Test
    fun `feiler med klientfeil hvis api returnerer feilstatus`() {
        startedWireMockServerWithCorrelationId {
            val errorMessage = "Noe gikk galt"
            stubFor(
                post(urlPathEqualTo("/maksimum"))
                    .willReturn(
                        aResponse()
                            .withStatus(500)
                            .withHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                            .withBody(errorMessage),
                    ),
            )

            val result = createClient(baseUrl()).hentMaksimum(
                fnr = Fnr("22503904369"),
                fraOgMedDato = LocalDate.now(),
                tilOgMedDato = LocalDate.now(),
            )

            result.shouldBeLeft().let { error: ClientError ->
                error.httpStatus shouldBe 500
                error.message shouldContain errorMessage
            }
        }
    }
}
