package no.nav.su.se.bakover.client.journalpost

import arrow.core.right
import com.github.tomakehurst.wiremock.client.WireMock
import dokument.domain.journalføring.Fagsystem
import dokument.domain.journalføring.KunneIkkeHenteJournalposter
import io.kotest.matchers.shouldBe
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
                client.finnesFagsak("1207CBF4", Fagsystem.INFOTRYGD).onLeft {
                    it.shouldBeType<KunneIkkeHenteJournalposter.ClientError>()
                }
            }
        }
    }

    @Test
    fun `produsert request er riktig`() {
        startedWireMockServerWithCorrelationId {
            stubFor(token("Bearer systemToken").willReturn(WireMock.ok(happyJson())))
            val expected = """
            {"query":"query(${"\$fagsak"}: FagsakInput! ${"\$tema"}: [Tema!]! ${"\$fraDato"}: Date ${"\$journalposttyper"}: [Journalposttype!]! ${"\$journalstatuser"}: [Journalstatus!]! ${"\$foerste"}: Int!) {\n    dokumentoversiktFagsak(\n            fagsak: ${"\$fagsak"}\n            tema: ${"\$tema"}\n            fraDato: ${"\$fraDato"}\n            journalposttyper: ${"\$journalposttyper"}\n            journalstatuser: ${"\$journalstatuser"}\n            foerste: ${"\$foerste"}\n    ){\n        journalposter {\n            tema\n            journalstatus\n            journalposttype\n            sak {\n                fagsakId\n            }\n            journalpostId\n            tittel\n            datoOpprettet\n        }\n    }\n}","variables":{"fagsak":{"fagsakId":"AC5960D","fagsaksystem":"Infotrygd"},"fraDato":null,"tema":[],"journalposttyper":[],"journalstatuser":[],"foerste":50}}
            """.trimIndent()

            setupClient(baseUrl()).also {
                it.finnesFagsak("AC5960D", Fagsystem.INFOTRYGD) shouldBe true.right()
                String(serveEvents.requests.first().request.body) shouldBe expected
            }
        }
    }

    private fun happyJson(): String {
        //language=JSON
        return """
        {
            "data": {
                "dokumentoversiktFagsak": {
                    "journalposter": [                        
                        {
                            "tema": "SUP",
                            "journalstatus": "JOURNALFOERT",
                            "journalposttype": "I",
                            "sak": {
                                "fagsakId": "AC5960D"
                            },
                            "journalpostId": "453812134",
                            "tittel": "Uttalelse",
                            "datoOpprettet": "2022-09-09T09:43:29"
                        },
                        {
                            "tema": "SUP",
                            "journalstatus": "JOURNALFOERT",
                            "journalposttype": "I",
                            "sak": {
                                "fagsakId": "AC5960D"
                            },
                            "journalpostId": "453812131",
                            "tittel": "NAV 00-03.01 NAV SU Kontrollnotat",
                            "datoOpprettet": "2022-09-09T09:30:42"
                        },
                        {
                            "tema": "SUP",
                            "journalstatus": "JOURNALFOERT",
                            "journalposttype": "I",
                            "sak": {
                                "fagsakId": "AC5960D"
                            },
                            "journalpostId": "453812131",
                            "tittel": "Dokumentasjon av oppfølgingssamtale",
                            "datoOpprettet": "2022-02-19T09:30:42"
                        }
                    ]
                }
            }
        }
        """.trimIndent()
    }
}
