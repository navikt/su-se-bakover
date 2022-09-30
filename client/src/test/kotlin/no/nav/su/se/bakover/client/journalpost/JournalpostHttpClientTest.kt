package no.nav.su.se.bakover.client.journalpost

import arrow.core.left
import arrow.core.right
import com.github.tomakehurst.wiremock.client.WireMock
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.client.AccessToken
import no.nav.su.se.bakover.client.WiremockBase
import no.nav.su.se.bakover.common.ApplicationConfig
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.journalpost.FerdigstiltJournalpost
import no.nav.su.se.bakover.domain.journalpost.JournalpostStatus
import no.nav.su.se.bakover.domain.journalpost.JournalpostTema
import no.nav.su.se.bakover.domain.journalpost.JournalpostType
import no.nav.su.se.bakover.domain.journalpost.KunneIkkeHenteJournalpost
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Isolated
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
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
    fun `henter journalpost OK`() {
        //language=JSON
        val suksessResponseJson =
            """
            {
              "data": {
                "journalpost": {
                  "tema": "SUP",
                  "journalstatus": "JOURNALFOERT",
                  "journalposttype": "I",
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

        setupClient().hentFerdigstiltJournalpost(Saksnummer(2021), JournalpostId("j")) shouldBe FerdigstiltJournalpost(
            tema = JournalpostTema.SUP,
            journalstatus = JournalpostStatus.JOURNALFOERT,
            journalposttype = JournalpostType.INNKOMMENDE_DOKUMENT,
            saksnummer = Saksnummer(2021),
        ).right()
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

        setupClient().hentFerdigstiltJournalpost(
            Saksnummer(2021),
            JournalpostId("j"),
        ) shouldBe KunneIkkeHenteJournalpost.TekniskFeil.left()
    }

    @Test
    fun `får ukjent feil dersom client kall feiler`() {
        WiremockBase.wireMockServer.stubFor(
            token("Bearer aadToken")
                .willReturn(WireMock.serverError()),
        )

        setupClient().hentFerdigstiltJournalpost(Saksnummer(2022), JournalpostId("j")) shouldBe KunneIkkeHenteJournalpost.Ukjent.left()
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

        setupClient().hentFerdigstiltJournalpost(
            Saksnummer(2022),
            JournalpostId("j"),
        ) shouldBe KunneIkkeHenteJournalpost.FantIkkeJournalpost.left()
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

internal fun setupClient() = JournalpostHttpClient(
    safConfig = ApplicationConfig.ClientsConfig.SafConfig(
        url = WiremockBase.wireMockServer.baseUrl(),
        clientId = "clientId",
    ),
    azureAd = mock {
        on { onBehalfOfToken(any(), any()) } doReturn "aadToken"
    },
    sts = mock {
        on { token() } doReturn AccessToken("stsToken")
    },
)
internal fun token(authorization: String) = WireMock.post(WireMock.urlPathEqualTo("/graphql"))
    .withHeader("Authorization", WireMock.equalTo(authorization))
    .withHeader("Content-Type", WireMock.equalTo("application/json"))
    .withHeader("Nav-Consumer-Id", WireMock.equalTo("su-se-bakover"))
