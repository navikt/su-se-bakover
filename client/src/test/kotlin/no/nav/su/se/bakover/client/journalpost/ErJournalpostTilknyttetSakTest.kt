package no.nav.su.se.bakover.client.journalpost

import arrow.core.left
import arrow.core.right
import com.github.tomakehurst.wiremock.client.WireMock
import dokument.domain.journalføring.ErTilknyttetSak
import dokument.domain.journalføring.KunneIkkeSjekkeTilknytningTilSak
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import no.nav.su.se.bakover.common.auth.AzureAd
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.infrastructure.config.ApplicationConfig
import no.nav.su.se.bakover.common.journal.JournalpostId
import no.nav.su.se.bakover.test.wiremock.startedWireMockServerWithCorrelationId
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

internal class JournalpostHttpClientTest {

    @Test
    fun `sjekker om journalpost er tilknyttet oppgitt saksnummer`() {
        startedWireMockServerWithCorrelationId {
            //language=JSON
            val suksessResponseJson =
                """
            {
              "data": {
                "journalpost": {
                  "sak": {
                    "fagsakId": "2021"
                  }
                }
              }
            }
                """.trimIndent()

            stubFor(
                token("Bearer onBehalfOfToken")
                    .willReturn(WireMock.ok(suksessResponseJson)),
            )

            runBlocking {
                setupClient(baseUrl()).erTilknyttetSak(
                    JournalpostId("j"),
                    Saksnummer(2021),
                ) shouldBe ErTilknyttetSak.Ja.right()
                setupClient(baseUrl()).erTilknyttetSak(
                    JournalpostId("j"),
                    Saksnummer(2023),
                ) shouldBe ErTilknyttetSak.Nei.right()
            }
        }
    }

    @Test
    fun `svarer nei dersom journalpost ikke er tilknyttet noen sak`() {
        startedWireMockServerWithCorrelationId {
            //language=JSON
            val suksessResponseJson =
                """
            {
              "data": {
                "journalpost": {
                  "sak": null
                }
              }
            }
                """.trimIndent()

            stubFor(
                token("Bearer onBehalfOfToken")
                    .willReturn(WireMock.ok(suksessResponseJson)),
            )
            runBlocking {
                setupClient(baseUrl()).erTilknyttetSak(
                    JournalpostId("j"),
                    Saksnummer(2021),
                ) shouldBe ErTilknyttetSak.Nei.right()
            }
        }
    }

    @Test
    fun `feil ved deserialisering av response`() {
        startedWireMockServerWithCorrelationId {
            //language=JSON
            val suksessResponseJson =
                """
            {
              "data": "{bogus content}"
            }
                """.trimIndent()

            stubFor(
                token("Bearer onBehalfOfToken")
                    .willReturn(WireMock.ok(suksessResponseJson)),
            )
            runBlocking {
                setupClient(baseUrl()).erTilknyttetSak(
                    JournalpostId("j"),
                    Saksnummer(2021),
                ) shouldBe KunneIkkeSjekkeTilknytningTilSak.TekniskFeil.left()
            }
        }
    }

    @Test
    fun `får ukjent feil dersom client kall feiler`() {
        startedWireMockServerWithCorrelationId {
            stubFor(
                token("Bearer onBehalfOfToken")
                    .willReturn(WireMock.serverError()),
            )

            runBlocking {
                setupClient(baseUrl()).erTilknyttetSak(
                    JournalpostId("j"),
                    Saksnummer(2022),
                ) shouldBe KunneIkkeSjekkeTilknytningTilSak.Ukjent.left()
            }
        }
    }

    @Test
    fun `fant ikke journalpost`() {
        startedWireMockServerWithCorrelationId {
            //language=JSON
            val errorResponseJson =
                """
            {
              "errors": [
                {
                  "message": "Fant ikke journalpost i fagarkivet. journalpostId=999999999",
                  "locations": [
                    {
                      "line": 2,
                      "column": 3
                    }
                  ],
                  "path": [
                    "journalpost"
                  ],
                  "extensions": {
                    "code": "not_found",
                    "classification": "ExecutionAborted"
                  }
                }
              ],
              "data": {
                "journalpost": null
              }
            }
                """.trimIndent()

            stubFor(
                token("Bearer onBehalfOfToken")
                    .willReturn(WireMock.ok(errorResponseJson)),
            )

            runBlocking {
                setupClient(baseUrl()).erTilknyttetSak(
                    JournalpostId("j"),
                    Saksnummer(2022),
                ) shouldBe KunneIkkeSjekkeTilknytningTilSak.FantIkkeJournalpost.left()
            }
        }
    }

    @Test
    fun `mapper fra graphql feil`() {
        skalMappeKodeTilRiktigErrorType(
            "forbidden",
            QueryJournalpostHttpClient.GraphQLApiFeil.HttpFeil.Forbidden("j", "du har feil"),
        )
        skalMappeKodeTilRiktigErrorType(
            "not_found",
            QueryJournalpostHttpClient.GraphQLApiFeil.HttpFeil.NotFound("j", "du har feil"),
        )
        skalMappeKodeTilRiktigErrorType(
            "bad_request",
            QueryJournalpostHttpClient.GraphQLApiFeil.HttpFeil.BadRequest("j", "du har feil"),
        )
        skalMappeKodeTilRiktigErrorType(
            "server_error",
            QueryJournalpostHttpClient.GraphQLApiFeil.HttpFeil.ServerError("j", "du har feil"),
        )
        skalMappeKodeTilRiktigErrorType(
            "top_secret",
            QueryJournalpostHttpClient.GraphQLApiFeil.HttpFeil.Ukjent("j", "du har feil"),
        )
    }

    private fun skalMappeKodeTilRiktigErrorType(
        code: String,
        expected: QueryJournalpostHttpClient.GraphQLApiFeil.HttpFeil,
    ) {
        val httpResponse = HentJournalpostHttpResponse(
            data = HentJournalpostResponse(null),
            errors = listOf(
                Error(
                    "du har feil",
                    listOf("journalpost"),
                    Extensions(
                        code,
                        "en eller annen classification",
                    ),
                ),
            ),
        )

        httpResponse.mapGraphQLHttpFeil("j") shouldBe expected
    }
}

internal fun setupClient(
    baseUrl: String,
    safConfig: ApplicationConfig.ClientsConfig.SafConfig = ApplicationConfig.ClientsConfig.SafConfig(
        url = baseUrl,
        clientId = "clientId",
    ),
    azureAd: AzureAd = mock {
        on { onBehalfOfToken(any(), any()) } doReturn "onBehalfOfToken"
        on { getSystemToken(any()) } doReturn "systemToken"
    },
) = QueryJournalpostHttpClient(
    safConfig = safConfig,
    azureAd = azureAd,
)

internal fun token(authorization: String) = WireMock.post(WireMock.urlPathEqualTo("/graphql"))
    .withHeader("Authorization", WireMock.equalTo(authorization))
    .withHeader("Content-Type", WireMock.equalTo("application/json"))
    .withHeader("Nav-Consumer-Id", WireMock.equalTo("su-se-bakover"))
