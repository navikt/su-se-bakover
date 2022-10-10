package no.nav.su.se.bakover.client.journalpost

import arrow.core.left
import arrow.core.right
import com.github.tomakehurst.wiremock.client.WireMock
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.client.AccessToken
import no.nav.su.se.bakover.client.WiremockBase
import no.nav.su.se.bakover.client.azure.AzureAd
import no.nav.su.se.bakover.client.sts.TokenOppslag
import no.nav.su.se.bakover.common.ApplicationConfig
import no.nav.su.se.bakover.common.application.journal.JournalpostId
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.journalpost.ErTilknyttetSak
import no.nav.su.se.bakover.domain.journalpost.JournalpostClientMetrics
import no.nav.su.se.bakover.domain.journalpost.KunneIkkeSjekkeTilknytningTilSak
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Isolated
import org.mockito.kotlin.any
import org.mockito.kotlin.doNothing
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.slf4j.MDC

// "denne må kjøres isolated siden MDC er static og vil tukle med andre tester som bruker MDC og kjører parallellt" - Quote from John Andre Hestad 2022
@Isolated
internal class JournalpostHttpClientTest {

    companion object {
        @BeforeAll
        @JvmStatic
        fun beforeAll() {
            MDC.put("Authorization", "lol")
        }

        @AfterAll
        @JvmStatic
        fun afterAll() {
            MDC.remove("Authorization")
        }
    }

    @Test
    fun `sjekker om journalpost er tilknyttet oppgitt saksnummer`() {
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

        WiremockBase.wireMockServer.stubFor(
            token("Bearer aadToken")
                .willReturn(WireMock.ok(suksessResponseJson)),
        )

        setupClient().erTilknyttetSak(JournalpostId("j"), Saksnummer(2021)) shouldBe ErTilknyttetSak.Ja.right()
        setupClient().erTilknyttetSak(JournalpostId("j"), Saksnummer(2023)) shouldBe ErTilknyttetSak.Nei.right()
    }

    @Test
    fun `svarer med feil dersom journalpost ikke er tilknyttet en sak i det heletatt`() {
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

        WiremockBase.wireMockServer.stubFor(
            token("Bearer aadToken")
                .willReturn(WireMock.ok(suksessResponseJson)),
        )

        setupClient().erTilknyttetSak(JournalpostId("j"), Saksnummer(2021)) shouldBe KunneIkkeSjekkeTilknytningTilSak.JournalpostIkkeKnyttetTilEnSak.left()
    }

    @Test
    fun `feil ved deserialisering av response`() {
        //language=JSON
        val suksessResponseJson =
            """
            {
              "data": "{bogus content}"
            }
            """.trimIndent()

        WiremockBase.wireMockServer.stubFor(
            token("Bearer aadToken")
                .willReturn(WireMock.ok(suksessResponseJson)),
        )

        setupClient().erTilknyttetSak(
            JournalpostId("j"),
            Saksnummer(2021),
        ) shouldBe KunneIkkeSjekkeTilknytningTilSak.TekniskFeil.left()
    }

    @Test
    fun `får ukjent feil dersom client kall feiler`() {
        WiremockBase.wireMockServer.stubFor(
            token("Bearer aadToken")
                .willReturn(WireMock.serverError()),
        )

        setupClient().erTilknyttetSak(JournalpostId("j"), Saksnummer(2022)) shouldBe KunneIkkeSjekkeTilknytningTilSak.Ukjent.left()
    }

    @Test
    fun `fant ikke journalpost`() {
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

        WiremockBase.wireMockServer.stubFor(
            token("Bearer aadToken")
                .willReturn(WireMock.ok(errorResponseJson)),
        )

        setupClient().erTilknyttetSak(
            JournalpostId("j"),
            Saksnummer(2022),
        ) shouldBe KunneIkkeSjekkeTilknytningTilSak.FantIkkeJournalpost.left()
    }

    @Test
    fun `mapper fra graphql feil`() {
        skalMappeKodeTilRiktigErrorType("forbidden", JournalpostHttpClient.GraphQLApiFeil.HttpFeil.Forbidden("j", "du har feil"))
        skalMappeKodeTilRiktigErrorType("not_found", JournalpostHttpClient.GraphQLApiFeil.HttpFeil.NotFound("j", "du har feil"))
        skalMappeKodeTilRiktigErrorType("bad_request", JournalpostHttpClient.GraphQLApiFeil.HttpFeil.BadRequest("j", "du har feil"))
        skalMappeKodeTilRiktigErrorType("server_error", JournalpostHttpClient.GraphQLApiFeil.HttpFeil.ServerError("j", "du har feil"))
        skalMappeKodeTilRiktigErrorType("top_secret", JournalpostHttpClient.GraphQLApiFeil.HttpFeil.Ukjent("j", "du har feil"))
    }

    private fun skalMappeKodeTilRiktigErrorType(code: String, expected: JournalpostHttpClient.GraphQLApiFeil.HttpFeil) {
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
    safConfig: ApplicationConfig.ClientsConfig.SafConfig = ApplicationConfig.ClientsConfig.SafConfig(
        url = WiremockBase.wireMockServer.baseUrl(),
        clientId = "clientId",
    ),
    azureAd: AzureAd = mock {
        on { onBehalfOfToken(any(), any()) } doReturn "aadToken"
    },
    sts: TokenOppslag = mock {
        on { token() } doReturn AccessToken("stsToken")
    },
    metrics: JournalpostClientMetrics = mock {
        doNothing().whenever(it).inkrementerBenyttetSkjema(any())
    },
) = JournalpostHttpClient(
    safConfig = safConfig,
    azureAd = azureAd,
    sts = sts,
    metrics = metrics,
)
internal fun token(authorization: String) = WireMock.post(WireMock.urlPathEqualTo("/graphql"))
    .withHeader("Authorization", WireMock.equalTo(authorization))
    .withHeader("Content-Type", WireMock.equalTo("application/json"))
    .withHeader("Nav-Consumer-Id", WireMock.equalTo("su-se-bakover"))
