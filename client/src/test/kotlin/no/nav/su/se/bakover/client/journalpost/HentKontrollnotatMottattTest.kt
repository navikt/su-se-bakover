package no.nav.su.se.bakover.client.journalpost

import arrow.core.right
import com.github.tomakehurst.wiremock.client.WireMock
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.client.WiremockBase
import no.nav.su.se.bakover.common.periode.januar
import no.nav.su.se.bakover.common.periode.september
import no.nav.su.se.bakover.common.periode.år
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.test.shouldBeType
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.parallel.Isolated

@Isolated
internal class HentKontrollnotatMottattTest : WiremockBase {

    @Test
    fun `svarer korrekt dersom sak har mottatt kontrollnotat i periode`() {
        WiremockBase.wireMockServer.stubFor(
            token("Bearer stsToken")
                .willReturn(WireMock.ok(happyJson())),
        )

        setupClient().also {
            it.kontrollnotatMotatt(Saksnummer(10002027), september(2022)) shouldBe true.right()
            it.kontrollnotatMotatt(Saksnummer(10002027), januar(2022)) shouldBe false.right()
            it.kontrollnotatMotatt(Saksnummer(10002027), år(2022)) shouldBe true.right()
        }
    }

    @Test
    fun `produsert request er riktig`() {
        WiremockBase.wireMockServer.stubFor(
            token("Bearer stsToken")
                .willReturn(WireMock.ok(happyJson())),
        )

        val expected = """
            {"query":"query(${"\$fagsakId"}: String!, ${"\$fagsaksystem"}: String! ${"\$fraDato"}: Date!, ${"\$tema"}: [Tema]!, ${"\$journalposttyper"}: [Journalposttype]!, ${"\$journalstatuser"}: [Journalstatus]!, ${"\$foerste"}:Int!) {\n  dokumentoversiktFagsak(fagsak:{fagsakId:${"\$fagsakId"},fagsaksystem:${"\$fagsaksystem"}}, fraDato:${"\$fraDato"}, tema:${"\$tema"}, journalposttyper:${"\$journalposttyper"}, journalstatuser:${"\$journalstatuser"}, foerste:${"\$foerste"}){\n    journalposter {\n  tema\n  journalstatus\n  journalposttype\n  sak {\n    fagsakId\n  }\n  journalpostId\n  tittel\n  datoOpprettet\n}\n  }\n}","variables":{"fagsakId":"10002027","fagsaksystem":"SUPSTONAD","fraDato":"2022-09-01","tema":"SUP","journalposttyper":["I"],"journalstatuser":["JOURNALFOERT"],"foerste":100}}
        """.trimIndent()

        setupClient().also {
            it.kontrollnotatMotatt(Saksnummer(10002027), september(2022))

            String(WiremockBase.wireMockServer.serveEvents.requests.first().request.body) shouldBe expected
        }
    }

    @Test
    fun `tryner med nullpointer dersom ikke alle felter returneres`() {
        //language=JSON
        val manglerFelterJson = """
            {
            "data": {
                "dokumentoversiktFagsak": {
                    "journalposter": [                        
                        {
                            "tema": "SUP",
                            "journalstatus": "JOURNALFOERT",
                            "journalposttype": "I",
                            "journalpostId": "453812134",
                            "tittel": "Uttalelse",
                            "datoOpprettet": "2022-09-09T09:43:29"
                        }                       
                    ]
                }
            }
        }
        """.trimIndent()
        WiremockBase.wireMockServer.stubFor(
            token("Bearer stsToken")
                .willReturn(WireMock.ok(manglerFelterJson)),
        )

        assertThrows<NullPointerException> {
            setupClient().also {
                it.kontrollnotatMotatt(Saksnummer(10002027), september(2022))
            }
        }
    }

    @Test
    fun `ingen journalposter funnet gir false`() {
        //language=JSON
        val manglerFelterJson = """
            {
            "data": {
                "dokumentoversiktFagsak": {
                    "journalposter": []
                }
            }
        }
        """.trimIndent()
        WiremockBase.wireMockServer.stubFor(
            token("Bearer stsToken")
                .willReturn(WireMock.ok(manglerFelterJson)),
        )

        setupClient().also {
            it.kontrollnotatMotatt(Saksnummer(10002027), september(2022)) shouldBe false.right()
        }
    }

    @Test
    fun `håndterer ukjente feil fra graphql`() {
        //language=JSON
        val manglerFelterJson = """
        {
            "errors": [
                {
                    "message": "Variable 'tema' has an invalid value: Invalid input for Enum 'Tema'. No value found for name 'BOGUS'",
                    "locations": [
                        {
                            "line": 1,
                            "column": 67
                        }
                    ],
                    "extensions": {
                        "classification": "ValidationError"
                    }
                }
            ]
        }
        """.trimIndent()
        WiremockBase.wireMockServer.stubFor(
            token("Bearer stsToken")
                .willReturn(WireMock.ok(manglerFelterJson)),
        )

        setupClient().also { client ->
            client.kontrollnotatMotatt(Saksnummer(10002027), september(2022)).tapLeft {
                it.feil.shouldBeType<JournalpostHttpClient.GraphQLApiFeil.HttpFeil.Ukjent>()
            }
        }
    }

    @Test
    fun `håndterer vanlige http feil`() {
        WiremockBase.wireMockServer.stubFor(
            token("Bearer stsToken")
                .willReturn(WireMock.unauthorized()),
        )

        setupClient().also { client ->
            client.kontrollnotatMotatt(Saksnummer(10002027), september(2022)).tapLeft {
                it.feil.shouldBeType<JournalpostHttpClient.GraphQLApiFeil.HttpFeil.Ukjent>()
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
                        }
                    ]
                }
            }
        }
        """.trimIndent()
    }
}
