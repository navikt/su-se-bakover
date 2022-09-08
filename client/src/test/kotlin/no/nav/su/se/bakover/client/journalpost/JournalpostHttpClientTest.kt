package no.nav.su.se.bakover.client.journalpost

import arrow.core.left
import arrow.core.right
import com.github.tomakehurst.wiremock.client.WireMock
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.client.WiremockBase
import no.nav.su.se.bakover.client.azure.AzureAd
import no.nav.su.se.bakover.client.stubs.sts.TokenOppslagStub
import no.nav.su.se.bakover.common.ApplicationConfig
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.journalpost.FerdigstiltJournalpost
import no.nav.su.se.bakover.domain.journalpost.JournalpostStatus
import no.nav.su.se.bakover.domain.journalpost.JournalpostType
import no.nav.su.se.bakover.domain.journalpost.KunneIkkeHenteJournalpost
import no.nav.su.se.bakover.domain.journalpost.Tema
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

    private val tokenOppslag = TokenOppslagStub

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
            wiremockBuilderOnBehalfOf("Bearer token")
                .willReturn(WireMock.ok(suksessResponseJson)),
        )

        val azureAdMock = mock<AzureAd> {
            on { onBehalfOfToken(any(), any()) } doReturn "token"
        }

        val client = JournalpostHttpClient(
            safConfig = ApplicationConfig.ClientsConfig.SafConfig(
                url = WiremockBase.wireMockServer.baseUrl(),
                clientId = "clientId",
            ),
            azureAd = azureAdMock,
        )

        client.hentFerdigstiltJournalpost(Saksnummer(2021), JournalpostId("j")) shouldBe FerdigstiltJournalpost.create(
            tema = Tema.SUP,
            journalstatus = JournalpostStatus.JOURNALFOERT,
            journalpostType = JournalpostType.INNKOMMENDE_DOKUMENT,
            saksnummer = Saksnummer(2021),
        ).right()
    }

    @Test
    fun `feil ved deserialisering av response`() {
        //language=JSON
        val suksessResponseJson =
            """
            {
              "data": null
            }
            """.trimIndent()

        WiremockBase.wireMockServer.stubFor(
            wiremockBuilderOnBehalfOf("Bearer token")
                .willReturn(WireMock.ok(suksessResponseJson)),
        )

        val azureAdMock = mock<AzureAd> {
            on { onBehalfOfToken(any(), any()) } doReturn "token"
        }

        val client = JournalpostHttpClient(
            safConfig = ApplicationConfig.ClientsConfig.SafConfig(
                url = WiremockBase.wireMockServer.baseUrl(),
                clientId = "clientId",
            ),
            azureAd = azureAdMock,
        )

        client.hentFerdigstiltJournalpost(
            Saksnummer(2021),
            JournalpostId("j"),
        ) shouldBe KunneIkkeHenteJournalpost.TekniskFeil.left()
    }

    @Test
    fun `får ukjent feil dersom client kall feiler`() {
        WiremockBase.wireMockServer.stubFor(
            wiremockBuilderOnBehalfOf("Bearer ${tokenOppslag.token().value}")
                .willReturn(WireMock.serverError()),
        )
        val azureAdMock = mock<AzureAd> {
            on { onBehalfOfToken(any(), any()) } doReturn "token"
        }

        val client = JournalpostHttpClient(
            safConfig = ApplicationConfig.ClientsConfig.SafConfig(
                url = WiremockBase.wireMockServer.baseUrl(),
                clientId = "clientId",
            ),
            azureAd = azureAdMock,
        )
        client.hentFerdigstiltJournalpost(Saksnummer(2022), JournalpostId("j")) shouldBe KunneIkkeHenteJournalpost.Ukjent.left()
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
            wiremockBuilderOnBehalfOf("Bearer token")
                .willReturn(WireMock.ok(errorResponseJson)),
        )

        val azureAdMock = mock<AzureAd> {
            on { onBehalfOfToken(any(), any()) } doReturn "token"
        }

        val client = JournalpostHttpClient(
            safConfig = ApplicationConfig.ClientsConfig.SafConfig(
                url = WiremockBase.wireMockServer.baseUrl(),
                clientId = "clientId",
            ),
            azureAd = azureAdMock,
        )
        client.hentFerdigstiltJournalpost(
            Saksnummer(2022),
            JournalpostId("j"),
        ) shouldBe KunneIkkeHenteJournalpost.FantIkkeJournalpost.left()
    }

    @Test
    fun `mapper fra graphql feil til KunneIkkeHenteJournalpost`() {
        skalMappeKodeTilRiktigErrorType("forbidden", KunneIkkeHenteJournalpost.IkkeTilgang)
        skalMappeKodeTilRiktigErrorType("not_found", KunneIkkeHenteJournalpost.FantIkkeJournalpost)
        skalMappeKodeTilRiktigErrorType("bad_request", KunneIkkeHenteJournalpost.UgyldigInput)
        skalMappeKodeTilRiktigErrorType("server_error", KunneIkkeHenteJournalpost.TekniskFeil)
        skalMappeKodeTilRiktigErrorType("top_secret", KunneIkkeHenteJournalpost.Ukjent)
    }

    private fun skalMappeKodeTilRiktigErrorType(code: String, expected: KunneIkkeHenteJournalpost) {
        val httpResponse = JournalpostHttpResponse(
            data = JournalpostResponse(null),
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

        httpResponse.tilKunneIkkeHenteJournalpost("j") shouldBe expected
    }

    private fun wiremockBuilderOnBehalfOf(authorization: String) = WireMock.post(WireMock.urlPathEqualTo("/graphql"))
        .withHeader("Authorization", WireMock.equalTo(authorization))
        .withHeader("Content-Type", WireMock.equalTo("application/json"))
        .withHeader("Nav-Consumer-Id", WireMock.equalTo("su-se-bakover"))
}
