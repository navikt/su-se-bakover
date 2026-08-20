package no.nav.su.se.bakover.client.historisk

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.containing
import com.github.tomakehurst.wiremock.client.WireMock.equalToJson
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import no.nav.su.se.bakover.common.auth.AzureAd
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.test.wiremock.startedWireMockServerWithCorrelationId
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

class SupstonadHistoriskClientTest {

    private fun mockAzureAd() = mock<AzureAd> {
        on { getSystemToken(any()) } doReturn "token"
    }

    private fun createClient(baseUrl: String): SupstonadHistoriskHttpClient {
        return SupstonadHistoriskHttpClient(
            azureAd = mockAzureAd(),
            url = "$baseUrl/",
            clientId = "clientId",
        )
    }

    @Test
    fun `henter uttrekk fra supstonad-historisk`() {
        startedWireMockServerWithCorrelationId {
            val expectedResponse = UttrekkResponse(
                iterator = "abc123",
                schema = SchemaDto(
                    kolonner = listOf(
                        KolonnebeskrivelseDto("fnr"),
                        KolonnebeskrivelseDto("beloep"),
                    ),
                ),
                innhold = listOf(
                    listOf("22503904369", "1000"),
                    listOf("12345678910", null),
                ),
            )
            stubFor(
                post(urlPathEqualTo("/api/hentUttrekk"))
                    .withHeader("Content-Type", containing("application/json"))
                    .withRequestBody(
                        equalToJson(
                            serialize(
                                UttrekkRequest(
                                    tabellnavn = "tabell",
                                    antallRader = 10,
                                ),
                            ),
                        ),
                    )
                    .willReturn(
                        aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody(serialize(expectedResponse)),
                    ),
            )

            val result = createClient(baseUrl()).hentUttrekk(tabellnavn = "tabell", antallRader = 10)

            result.shouldBeRight(expectedResponse)
        }
    }

    @Test
    fun `henter uttrekk med iterator fra supstonad-historisk`() {
        startedWireMockServerWithCorrelationId {
            val expectedResponse = UttrekkResponse(
                iterator = "",
                schema = SchemaDto(kolonner = listOf(KolonnebeskrivelseDto("fnr"))),
                innhold = emptyList(),
            )
            stubFor(
                post(urlPathEqualTo("/api/hentUttrekk"))
                    .withHeader("Content-Type", containing("application/json"))
                    .withRequestBody(
                        equalToJson(
                            serialize(
                                UttrekkRequest(
                                    tabellnavn = "tabell",
                                    iterator = "abc123",
                                    antallRader = 10,
                                ),
                            ),
                        ),
                    )
                    .willReturn(
                        aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody(serialize(expectedResponse)),
                    ),
            )

            val result = createClient(baseUrl()).hentUttrekk(tabellnavn = "tabell", antallRader = 10, iterator = "abc123")

            result.shouldBeRight(expectedResponse)
        }
    }

    @Test
    fun `teller rader i tabell fra supstonad-historisk`() {
        startedWireMockServerWithCorrelationId {
            val expectedResponse = CountResponse(antall = 42)
            stubFor(
                post(urlPathEqualTo("/api/tellRader"))
                    .withHeader("Content-Type", containing("application/json"))
                    .withRequestBody(equalToJson(serialize(CountRequest(tabellnavn = "tabell"))))
                    .willReturn(
                        aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody(serialize(expectedResponse)),
                    ),
            )

            val result = createClient(baseUrl()).tellRader(tabellnavn = "tabell")

            result.shouldBeRight(expectedResponse)
        }
    }

    @Test
    fun `henter tabeller fra supstonad-historisk`() {
        startedWireMockServerWithCorrelationId {
            val expectedResponse = mapOf(
                "t1" to listOf("fnr", "beloep"),
                "t2" to listOf("fnr"),
            )
            stubFor(
                get(urlPathEqualTo("/tables"))
                    .willReturn(
                        aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody(serialize(expectedResponse)),
                    ),
            )

            val result = createClient(baseUrl()).hentTabeller()

            result.shouldBeRight(expectedResponse)
        }
    }

    @Test
    fun `feiler med http-feil fra tellRader`() {
        startedWireMockServerWithCorrelationId {
            val body = "Feil ved telling av rader"
            stubFor(
                post(urlPathEqualTo("/api/tellRader"))
                    .withHeader("Content-Type", containing("application/json"))
                    .willReturn(
                        aResponse()
                            .withStatus(500)
                            .withHeader("Content-Type", "application/json")
                            .withBody(body),
                    ),
            )

            val result = createClient(baseUrl()).tellRader(tabellnavn = "tabell")

            result.shouldBeLeft().let { clientError ->
                clientError.httpStatus shouldBe 500
                clientError.message shouldContain body
            }
        }
    }

    @Test
    fun `feiler med http-feil fra hentUttrekk`() {
        startedWireMockServerWithCorrelationId {
            val body = "Feil ved henting av uttrekk"
            stubFor(
                post(urlPathEqualTo("/api/hentUttrekk"))
                    .withHeader("Content-Type", containing("application/json"))
                    .willReturn(
                        aResponse()
                            .withStatus(500)
                            .withHeader("Content-Type", "application/json")
                            .withBody(body),
                    ),
            )

            val result = createClient(baseUrl()).hentUttrekk(tabellnavn = "tabell", antallRader = 10)

            result.shouldBeLeft().let { clientError ->
                clientError.httpStatus shouldBe 500
                clientError.message shouldContain body
            }
        }
    }
}
