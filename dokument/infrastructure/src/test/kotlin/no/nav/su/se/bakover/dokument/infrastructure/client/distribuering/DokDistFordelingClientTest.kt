package no.nav.su.se.bakover.dokument.infrastructure.client.distribuering

import arrow.core.right
import com.github.tomakehurst.wiremock.client.MappingBuilder
import com.github.tomakehurst.wiremock.client.WireMock
import dokument.domain.Distribusjonstidspunkt
import dokument.domain.Distribusjonstype
import dokument.domain.brev.BrevbestillingId
import dokument.domain.distribuering.Distribueringsadresse
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.client.stubs.azure.AzureClientStub
import no.nav.su.se.bakover.common.infrastructure.config.ApplicationConfig
import no.nav.su.se.bakover.common.journal.JournalpostId
import no.nav.su.se.bakover.test.wiremock.startedWireMockServerWithCorrelationId
import org.junit.jupiter.api.Test

internal class DokDistFordelingClientTest {

    @Test
    fun `happycase uten adresse`() {
        startedWireMockServerWithCorrelationId {
            val journalpostId = JournalpostId("1")
            val distribusjonstype = Distribusjonstype.VEDTAK
            val distribusjonstidspunkt = Distribusjonstidspunkt.KJERNETID
            val client = DokDistFordelingClient(
                dokDistConfig = ApplicationConfig.ClientsConfig.DokDistConfig(baseUrl(), "clientId"),
                azureAd = AzureClientStub,
            )
            stubFor(
                wiremockBuilder
                    .withRequestBody(
                        WireMock.equalToJson(
                            """
                             {
                                "journalpostId": "$journalpostId",
                                "bestillendeFagsystem": "SUPSTONAD",
                                "dokumentProdApp": "SU_SE_BAKOVER",
                                "distribusjonstype": "VEDTAK",
                                "distribusjonstidspunkt": "KJERNETID",
                                "adresse": null
                            }
                            """.trimIndent(),
                        ),
                    )
                    .willReturn(
                        WireMock.okJson(
                            """
                        {
                            "bestillingsId": "id på tingen"
                        }
                            """.trimIndent(),
                        ),
                    ),
            )
            client.bestillDistribusjon(
                journalpostId,
                distribusjonstype,
                distribusjonstidspunkt,
            ) shouldBe BrevbestillingId("id på tingen").right()
        }
    }

    @Test
    fun `Skal sette feilregistrert på feilregistrerte manuelle journalposter`() {
        startedWireMockServerWithCorrelationId {
            val journalpostId = JournalpostId("1")
            val distribusjonstype = Distribusjonstype.VEDTAK
            val distribusjonstidspunkt = Distribusjonstidspunkt.KJERNETID
            val client = DokDistFordelingClient(
                dokDistConfig = ApplicationConfig.ClientsConfig.DokDistConfig(baseUrl(), "clientId"),
                azureAd = AzureClientStub,
            )
            stubFor(
                wiremockBuilder
                    .withRequestBody(
                        WireMock.equalToJson(
                            """
                             {
                                "journalpostId": "$journalpostId",
                                "bestillendeFagsystem": "SUPSTONAD",
                                "dokumentProdApp": "SU_SE_BAKOVER",
                                "distribusjonstype": "VEDTAK",
                                "distribusjonstidspunkt": "KJERNETID",
                                "adresse": null
                            }
                            """.trimIndent(),
                        ),
                    )
                    .willReturn(
                        WireMock.aResponse()
                            .withStatus(400)
                            .withHeader("Content-Type", "application/json")
                            .withBody(
                                """
                    {
                        "timestamp":"2025-07-22T15:43:37.893+00:00",
                        "error": "Bad Request",
                        "message": "Validering av distribusjonsforespørsel feilet med feilmelding: Journalpostfeltet journalpoststatus er ikke som forventet, fikk: FEILREGISTRERT, men forventet FERDIGSTILT"
                    }
                                """.trimIndent(),
                            ),
                    ),
            )
            client.bestillDistribusjon(
                journalpostId,
                distribusjonstype,
                distribusjonstidspunkt,
            ) shouldBe BrevbestillingId("FEILREGISTRERT").right()
        }
    }

    @Test
    fun `happycase med adresse`() {
        startedWireMockServerWithCorrelationId {
            val journalpostId = JournalpostId("1")
            val distribusjonstype = Distribusjonstype.VEDTAK
            val distribusjonstidspunkt = Distribusjonstidspunkt.KJERNETID
            val distribueringsadresse = Distribueringsadresse(
                adresselinje1 = "adresse1",
                adresselinje2 = null,
                adresselinje3 = null,
                postnummer = "1234",
                poststed = "poststed",
            )
            val client = DokDistFordelingClient(
                dokDistConfig = ApplicationConfig.ClientsConfig.DokDistConfig(baseUrl(), "clientId"),
                azureAd = AzureClientStub,
            )
            stubFor(
                wiremockBuilder
                    .withRequestBody(
                        WireMock.equalToJson(
                            """
                             {
                                "journalpostId": "$journalpostId",
                                "bestillendeFagsystem": "SUPSTONAD",
                                "dokumentProdApp": "SU_SE_BAKOVER",
                                "distribusjonstype": "VEDTAK",
                                "distribusjonstidspunkt": "KJERNETID",
                                "adresse": {
                                    "adressetype":"norskPostadresse",
                                    "adresselinje1":"adresse1",
                                    "adresselinje2":null,
                                    "adresselinje3":null,
                                    "postnummer":"1234",
                                    "poststed":"poststed",
                                    "land":"NO"
                                }
                            }
                            """.trimIndent(),
                        ),
                    )
                    .willReturn(
                        WireMock.okJson(
                            """
                        {
                            "bestillingsId": "id på tingen"
                        }
                            """.trimIndent(),
                        ),
                    ),
            )
            client.bestillDistribusjon(
                journalpostId = journalpostId,
                distribusjonstype = distribusjonstype,
                distribusjonstidspunkt = distribusjonstidspunkt,
                distribueringsadresse = distribueringsadresse,
            ) shouldBe BrevbestillingId("id på tingen").right()
        }
    }

    @Test
    fun `returnerer brevbestillingsId'en dersom responsen er en 409`() {
        startedWireMockServerWithCorrelationId {
            val client = DokDistFordelingClient(
                dokDistConfig = ApplicationConfig.ClientsConfig.DokDistConfig(baseUrl(), "clientId"),
                azureAd = AzureClientStub,
            )
            val journalpostId = JournalpostId("1")
            val distribusjonstype = Distribusjonstype.VEDTAK
            val distribusjonstidspunkt = Distribusjonstidspunkt.KJERNETID
            stubFor(
                wiremockBuilder
                    .withRequestBody(
                        WireMock.equalToJson(
                            """
                             {
                                "journalpostId": "$journalpostId",
                                "bestillendeFagsystem": "SUPSTONAD",
                                "dokumentProdApp": "SU_SE_BAKOVER",
                                "distribusjonstype": "VEDTAK",
                                "distribusjonstidspunkt": "KJERNETID",
                                "adresse": null
                            }
                            """.trimIndent(),
                        ),
                    )
                    .willReturn(WireMock.jsonResponse("""{"bestillingsId": "123-456"}""", 409)),
            )

            client.bestillDistribusjon(
                journalpostId,
                distribusjonstype,
                distribusjonstidspunkt,
            ) shouldBe BrevbestillingId("123-456").right()
        }
    }

    @Test
    fun `dersom brevbestillingsId ikke finnes ved en gir vi en default brevbestillingsId`() {
        startedWireMockServerWithCorrelationId {
            val client = DokDistFordelingClient(
                dokDistConfig = ApplicationConfig.ClientsConfig.DokDistConfig(baseUrl(), "clientId"),
                azureAd = AzureClientStub,
            )
            val journalpostId = JournalpostId("1")
            val distribusjonstype = Distribusjonstype.VEDTAK
            val distribusjonstidspunkt = Distribusjonstidspunkt.KJERNETID
            stubFor(
                wiremockBuilder
                    .withRequestBody(
                        WireMock.equalToJson(
                            """
                             {
                                "journalpostId": "$journalpostId",
                                "bestillendeFagsystem": "SUPSTONAD",
                                "dokumentProdApp": "SU_SE_BAKOVER",
                                "distribusjonstype": "VEDTAK",
                                "distribusjonstidspunkt": "KJERNETID",
                                "adresse": null
                            }
                            """.trimIndent(),
                        ),
                    )
                    .willReturn(WireMock.jsonResponse("""{"biggus": "dickus"}""", 409)),
            )

            client.bestillDistribusjon(
                journalpostId,
                distribusjonstype,
                distribusjonstidspunkt,
            ) shouldBe BrevbestillingId("ikke_mottatt_fra_ekstern_tjeneste").right()
        }
    }

    private val wiremockBuilder: MappingBuilder = WireMock.post(WireMock.urlPathEqualTo(DOK_DIST_FORDELING_PATH))
        .withHeader("Authorization", WireMock.equalTo("Bearer token"))
        .withHeader("Content-Type", WireMock.equalTo("application/json"))
        .withHeader("Accept", WireMock.equalTo("application/json"))
        .withHeader("Nav-CallId", WireMock.equalTo("correlationId"))
}
