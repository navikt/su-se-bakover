package no.nav.su.se.bakover.client.journalpost

import com.github.tomakehurst.wiremock.client.WireMock
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.client.WiremockBase
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.domain.journalpost.KunneIkkeHenteJournalposter
import no.nav.su.se.bakover.test.shouldBeType
import org.junit.jupiter.api.Test

class HentJournalposterForTest {

    @Test
    fun `håndterer vanlige http feil`() {
        WiremockBase.wireMockServer.stubFor(
            token("Bearer stsToken")
                .willReturn(WireMock.unauthorized()),
        )

        setupClient().also { client ->
            client.hentJournalposterFor(Saksnummer(10002027)).onLeft {
                it.shouldBeType<KunneIkkeHenteJournalposter.ClientError>()
            }
        }
    }

    @Test
    fun `produsert request er riktig`() {
        WiremockBase.wireMockServer.stubFor(token("Bearer stsToken").willReturn(WireMock.ok(happyJson())))
        val expected = """
            {"query":"query(${"\$fagsak"}: FagsakInput! ${"\$tema"}: [Tema!]! ${"\$fraDato"}: Date ${"\$journalposttyper"}: [Journalposttype!]! ${"\$journalstatuser"}: [Journalstatus!]! ${"\$foerste"}: Int!) {\n    dokumentoversiktFagsak(\n            fagsak: ${"\$fagsak"}\n            tema: ${"\$tema"}\n            fraDato: ${"\$fraDato"}\n            journalposttyper: ${"\$journalposttyper"}\n            journalstatuser: ${"\$journalstatuser"}\n            foerste: ${"\$foerste"}\n    ){\n        journalposter {\n            tema\n            journalstatus\n            journalposttype\n            sak {\n                fagsakId\n            }\n            journalpostId\n            tittel\n            datoOpprettet\n        }\n    }\n}","variables":{"fagsak":{"fagsakId":"10002027","fagsaksystem":"SUPSTONAD"},"fraDato":null,"tema":"SUP","journalposttyper":[],"journalstatuser":[],"foerste":50}}
        """.trimIndent()

        setupClient().also {
            it.hentJournalposterFor(Saksnummer(10002027))
            String(WiremockBase.wireMockServer.serveEvents.requests.first().request.body) shouldBe expected
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
                                "fagsakId": "10002027"
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
                                "fagsakId": "10002027"
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
                                "fagsakId": "10002027"
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
