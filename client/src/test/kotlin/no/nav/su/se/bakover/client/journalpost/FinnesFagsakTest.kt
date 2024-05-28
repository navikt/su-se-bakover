package no.nav.su.se.bakover.client.journalpost

import arrow.core.right
import com.github.tomakehurst.wiremock.client.WireMock
import dokument.domain.journalføring.KunneIkkeHenteJournalposter
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.test.generer
import no.nav.su.se.bakover.test.shouldBeType
import no.nav.su.se.bakover.test.wiremock.startedWireMockServerWithCorrelationId
import org.junit.jupiter.api.Test

class FinnesFagsakTest {

    @Test
    fun `håndterer vanlige http feil`() {
        startedWireMockServerWithCorrelationId {
            stubFor(
                token("Bearer systemToken")
                    .willReturn(WireMock.unauthorized()),
            )

            setupClient(baseUrl()).also { client ->
                client.finnesFagsak(Fnr.generer(), "1207CBF4").onLeft {
                    it.shouldBeType<KunneIkkeHenteJournalposter.ClientError>()
                }
            }
        }
    }

    @Test
    fun `produsert request er riktig`() {
        val fnr = Fnr.generer()
        val fagsystemId = "1207CBF4"
        startedWireMockServerWithCorrelationId {
            stubFor(token("Bearer systemToken").willReturn(WireMock.ok(happyJson())))
            val expected = """
            {"query":"query(${"\$brukerId"}: BrukerIdInput! ${"\$fraDato"}: Date ${"\$tema"}: [Tema!]! ${"\$journalposttyper"}: [Journalposttype!]! ${"\$journalstatuser"}: [Journalstatus!]! ${"\$foerste"}: Int!) {\n    dokumentoversiktBruker(\n            brukerId: ${"\$brukerId"}\n            fraDato: ${"\$fraDato"}\n            tema: ${"\$tema"}\n            journalposttyper: ${"\$journalposttyper"}\n            journalstatuser: ${"\$journalstatuser"}\n            foerste: ${"\$foerste"}\n    ){\n        journalposter {\n            sak {\n                fagsakId\n            }\n        }\n    }\n}","variables":{"brukerId":{"id":"$fnr","type":"FNR"},"fraDato":null,"tilDato":null,"tema":["SUP"],"journalposttyper":[],"journalstatuser":[],"foerste":100}}
            """.trimIndent()

            setupClient(baseUrl()).also {
                it.finnesFagsak(fnr, fagsystemId) shouldBe true.right()
                String(serveEvents.requests.first().request.body) shouldBe expected
            }
        }
    }

    private fun happyJson(): String {
        //language=JSON
        return """
        {
            "data": {
                "dokumentoversiktBruker": {
                    "journalposter": [                        
                        {"sak": {"fagsakId": "AC5960D"}},
                        {"sak": {"fagsakId": "1234A12"}},
                        {"sak": {"fagsakId": "1207CBF4"}
                        }
                    ]
                }
            }
        }
        """.trimIndent()
    }
}
