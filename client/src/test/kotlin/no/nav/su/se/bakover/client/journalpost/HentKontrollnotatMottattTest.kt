package no.nav.su.se.bakover.client.journalpost

import arrow.core.right
import com.github.tomakehurst.wiremock.client.WireMock
import dokument.domain.journalføring.ErKontrollNotatMottatt
import dokument.domain.journalføring.JournalpostStatus
import dokument.domain.journalføring.JournalpostTema
import dokument.domain.journalføring.JournalpostType
import dokument.domain.journalføring.KontrollnotatMottattJournalpost
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.domain.tid.februar
import no.nav.su.se.bakover.common.domain.tid.september
import no.nav.su.se.bakover.common.journal.JournalpostId
import no.nav.su.se.bakover.common.tid.periode.februar
import no.nav.su.se.bakover.common.tid.periode.januar
import no.nav.su.se.bakover.common.tid.periode.september
import no.nav.su.se.bakover.common.tid.periode.år
import no.nav.su.se.bakover.test.shouldBeType
import no.nav.su.se.bakover.test.wiremock.startedWireMockServerWithCorrelationId
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

internal class HentKontrollnotatMottattTest {

    @Test
    fun `svarer korrekt dersom sak har mottatt kontrollnotat i periode`() {
        startedWireMockServerWithCorrelationId {
            stubFor(
                token("Bearer systemToken")
                    .willReturn(WireMock.ok(happyJson())),
            )

            setupClient(
                baseUrl = baseUrl(),
            ).also {
                it.kontrollnotatMotatt(Saksnummer(10002027), september(2022)) shouldBe ErKontrollNotatMottatt.Ja(
                    kontrollnotat = KontrollnotatMottattJournalpost(
                        tema = JournalpostTema.SUP,
                        journalstatus = JournalpostStatus.JOURNALFOERT,
                        journalposttype = JournalpostType.INNKOMMENDE_DOKUMENT,
                        saksnummer = Saksnummer(10002027),
                        tittel = "NAV 00-03.01 NAV SU Kontrollnotat",
                        datoOpprettet = 9.september(2022),
                        journalpostId = JournalpostId(value = "453812131"),
                    ),
                ).right()

                it.kontrollnotatMotatt(Saksnummer(10002027), januar(2022)) shouldBe ErKontrollNotatMottatt.Nei.right()

                it.kontrollnotatMotatt(Saksnummer(10002027), år(2022)) shouldBe ErKontrollNotatMottatt.Ja(
                    kontrollnotat = KontrollnotatMottattJournalpost(
                        tema = JournalpostTema.SUP,
                        journalstatus = JournalpostStatus.JOURNALFOERT,
                        journalposttype = JournalpostType.INNKOMMENDE_DOKUMENT,
                        saksnummer = Saksnummer(10002027),
                        tittel = "NAV 00-03.01 NAV SU Kontrollnotat",
                        datoOpprettet = 9.september(2022),
                        journalpostId = JournalpostId(value = "453812131"),
                    ),
                ).right()

                it.kontrollnotatMotatt(Saksnummer(10002027), februar(2022)) shouldBe ErKontrollNotatMottatt.Ja(
                    kontrollnotat = KontrollnotatMottattJournalpost(
                        tema = JournalpostTema.SUP,
                        journalstatus = JournalpostStatus.JOURNALFOERT,
                        journalposttype = JournalpostType.INNKOMMENDE_DOKUMENT,
                        saksnummer = Saksnummer(10002027),
                        tittel = "Dokumentasjon av oppfølgingssamtale",
                        datoOpprettet = 19.februar(2022),
                        journalpostId = JournalpostId(value = "453812131"),
                    ),
                ).right()
            }
        }
    }

    // TODO: for testing enablet
    @Disabled
    @Test
    fun `produsert request er riktig`() {
        startedWireMockServerWithCorrelationId {
            stubFor(
                token("Bearer systemToken")
                    .willReturn(WireMock.ok(happyJson())),
            )

            val expected = """
            {"query":"query(${"\$fagsak"}: FagsakInput! ${"\$tema"}: [Tema!]! ${"\$fraDato"}: Date ${"\$journalposttyper"}: [Journalposttype!]! ${"\$journalstatuser"}: [Journalstatus!]! ${"\$foerste"}: Int!) {\n    dokumentoversiktFagsak(\n            fagsak: ${"\$fagsak"}\n            tema: ${"\$tema"}\n            fraDato: ${"\$fraDato"}\n            journalposttyper: ${"\$journalposttyper"}\n            journalstatuser: ${"\$journalstatuser"}\n            foerste: ${"\$foerste"}\n    ){\n        journalposter {\n            tema\n            journalstatus\n            journalposttype\n            sak {\n                fagsakId\n            }\n            journalpostId\n            tittel\n            datoOpprettet\n        }\n    }\n}","variables":{"fagsak":{"fagsakId":"10002027","fagsaksystem":"SUPSTONAD"},"fraDato":"2022-09-01","tema":["SUP"],"journalposttyper":["I"],"journalstatuser":["JOURNALFOERT"],"foerste":100}}
            """.trimIndent()

            setupClient(baseUrl = baseUrl()).also {
                it.kontrollnotatMotatt(Saksnummer(10002027), september(2022))

                String(serveEvents.requests.first().request.body) shouldBe expected
            }
        }
    }

    @Test
    fun `tryner med nullpointer dersom ikke alle felter returneres`() {
        startedWireMockServerWithCorrelationId {
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
            stubFor(
                token("Bearer systemToken")
                    .willReturn(WireMock.ok(manglerFelterJson)),
            )

            assertThrows<NullPointerException> {
                setupClient(baseUrl()).also {
                    it.kontrollnotatMotatt(Saksnummer(10002027), september(2022))
                }
            }
        }
    }

    @Test
    fun `ingen journalposter funnet gir false`() {
        startedWireMockServerWithCorrelationId {
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
            stubFor(
                token("Bearer systemToken")
                    .willReturn(WireMock.ok(manglerFelterJson)),
            )

            setupClient(baseUrl()).also {
                it.kontrollnotatMotatt(
                    Saksnummer(10002027),
                    september(2022),
                ) shouldBe ErKontrollNotatMottatt.Nei.right()
            }
        }
    }

    @Test
    fun `håndterer ukjente feil fra graphql`() {
        startedWireMockServerWithCorrelationId {
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
            stubFor(
                token("Bearer systemToken")
                    .willReturn(WireMock.ok(manglerFelterJson)),
            )

            setupClient(baseUrl()).also { client ->
                client.kontrollnotatMotatt(Saksnummer(10002027), september(2022)).onLeft {
                    it.feil.shouldBeType<QueryJournalpostHttpClient.GraphQLApiFeil.HttpFeil.Ukjent>()
                }
            }
        }
    }

    @Test
    fun `håndterer vanlige http feil`() {
        startedWireMockServerWithCorrelationId {
            stubFor(
                token("Bearer systemToken")
                    .willReturn(WireMock.unauthorized()),
            )

            setupClient(baseUrl()).also { client ->
                client.kontrollnotatMotatt(Saksnummer(10002027), september(2022)).onLeft {
                    it.feil.shouldBeType<QueryJournalpostHttpClient.GraphQLApiFeil.HttpFeil.Ukjent>()
                }
            }
        }
    }

    @Test
    fun `velger nyeste journalpost dersom det eksisterer flere for periode`() {
        startedWireMockServerWithCorrelationId {
            val flereKontrollnotat = """
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
                            "journalpostId": "453899999",
                            "tittel": "NAV 00-03.01 NAV SU Kontrollnotat",
                            "datoOpprettet": "2022-09-28T09:30:42"
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

            stubFor(
                token("Bearer systemToken")
                    .willReturn(WireMock.ok(flereKontrollnotat)),
            )

            setupClient(baseUrl()).also { client ->
                client.kontrollnotatMotatt(Saksnummer(10002027), september(2022)) shouldBe ErKontrollNotatMottatt.Ja(
                    kontrollnotat = KontrollnotatMottattJournalpost(
                        tema = JournalpostTema.SUP,
                        journalstatus = JournalpostStatus.JOURNALFOERT,
                        journalposttype = JournalpostType.INNKOMMENDE_DOKUMENT,
                        saksnummer = Saksnummer(10002027),
                        tittel = "NAV 00-03.01 NAV SU Kontrollnotat",
                        datoOpprettet = 28.september(2022),
                        journalpostId = JournalpostId(value = "453899999"),
                    ),
                ).right()
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
