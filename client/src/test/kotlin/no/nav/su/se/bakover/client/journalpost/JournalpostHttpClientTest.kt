package no.nav.su.se.bakover.client.journalpost

import arrow.core.left
import arrow.core.right
import com.github.tomakehurst.wiremock.client.WireMock
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.client.WiremockBase
import no.nav.su.se.bakover.client.stubs.sts.TokenOppslagStub
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.journalpost.HentetJournalpost
import no.nav.su.se.bakover.domain.journalpost.KunneIkkeHenteJournalpost
import org.junit.jupiter.api.Test

internal class JournalpostHttpClientTest {

    private val tokenOppslag = TokenOppslagStub

    @Test
    fun `henter journalpost OK`() {

        //language=JSON
        val suksessResponseJson =
            """
            {
              "data": {
                "journalpost": {
                  "tema": "SUP"
                }
              }
            }
            """.trimIndent()

        WiremockBase.wireMockServer.stubFor(
            wiremockBuilderOnBehalfOf("Bearer token")
                .willReturn(WireMock.ok(suksessResponseJson)),
        )

        val client = JournalpostHttpClient(
            safUrl = WiremockBase.wireMockServer.baseUrl(),
            tokenOppslag = tokenOppslag,
        )

        client.hentJournalpost(JournalpostId("j")) shouldBe HentetJournalpost.create(tema = "SUP").right()
    }

    @Test
    fun `får ukjent feil dersom client kall feiler`() {
        WiremockBase.wireMockServer.stubFor(
            wiremockBuilderOnBehalfOf("Bearer ${tokenOppslag.token()}")
                .willReturn(WireMock.serverError()),
        )

        val client = JournalpostHttpClient(
            safUrl = WiremockBase.wireMockServer.baseUrl(),
            tokenOppslag = tokenOppslag,
        )
        client.hentJournalpost(JournalpostId("j")) shouldBe KunneIkkeHenteJournalpost.Ukjent.left()
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

        val client = JournalpostHttpClient(
            safUrl = WiremockBase.wireMockServer.baseUrl(),
            tokenOppslag = tokenOppslag,
        )
        client.hentJournalpost(JournalpostId("j")) shouldBe KunneIkkeHenteJournalpost.FantIkkeJournalpost.left()
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

        httpResponse.tilKunneIkkeHenteJournalpost() shouldBe expected
    }

    private fun wiremockBuilderOnBehalfOf(authorization: String) = WireMock.post(WireMock.urlPathEqualTo("/"))
        .withHeader("Authorization", WireMock.equalTo(authorization))
        .withHeader("Content-Type", WireMock.equalTo("application/json"))
        .withHeader("Nav-Consumer-Id", WireMock.equalTo("su-se-bakover"))
}
