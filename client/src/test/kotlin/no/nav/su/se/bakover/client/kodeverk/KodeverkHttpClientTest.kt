package no.nav.su.se.bakover.client.kodeverk

import arrow.core.left
import arrow.core.right
import com.github.tomakehurst.wiremock.client.WireMock
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.client.WiremockBase
import no.nav.su.se.bakover.client.WiremockBase.Companion.putCorrelationId
import no.nav.su.se.bakover.client.WiremockBase.Companion.removeCorrelationId
import no.nav.su.se.bakover.client.WiremockBase.Companion.wireMockServer
import no.nav.su.se.bakover.client.kodeverk.Kodeverk.CouldNotGetKode
import org.junit.jupiter.api.Test

internal class KodeverkHttpClientTest : WiremockBase {

    @Test
    fun `Sjekk at vi finner poststed`() {
        wireMockServer.stubFor(
            wiremockBuilder(KODEVERK_POSTSTED_PATH)
                .willReturn(
                    WireMock.ok(resultatPoststedJson),
                ),
        )
        val client = KodeverkHttpClient(wireMockServer.baseUrl(), "srvsupstonad")
        client.hentPoststed("1479") shouldBe "KURLAND".right()
    }

    @Test
    fun `Sjekk at vi takler manglende correlationid`() {
        wireMockServer.also { removeCorrelationId() }.stubFor(
            WireMock.get(WireMock.urlPathEqualTo(KODEVERK_POSTSTED_PATH))
                .withHeader("Content-Type", WireMock.equalTo("application/json"))
                .withHeader("Nav-Call-Id", WireMock.matching(".+"))
                .withHeader("Nav-Consumer-Id", WireMock.equalTo("srvsupstonad"))
                .withQueryParam("ekskluderUgyldige", WireMock.equalTo("true"))
                .withQueryParam("spraak", WireMock.equalTo("nb"))
                .willReturn(
                    WireMock.ok(resultatPoststedJson),
                ),
        )

        val client = KodeverkHttpClient(wireMockServer.baseUrl(), "srvsupstonad")
        client.hentPoststed("1479") shouldBe "KURLAND".right()
        putCorrelationId()
    }

    @Test
    fun `Sjekk at vi får null hvis postnummer ikke finnes`() {
        wireMockServer.stubFor(
            wiremockBuilder(KODEVERK_POSTSTED_PATH)
                .willReturn(
                    WireMock.ok(resultatPoststedJson),
                ),
        )
        val client = KodeverkHttpClient(wireMockServer.baseUrl(), "srvsupstonad")
        client.hentPoststed("XXXX") shouldBe null.right()
    }

    @Test
    fun `Sjekk at vi får CouldNotGetKodeverk`() {
        wireMockServer.stubFor(
            wiremockBuilder(KODEVERK_POSTSTED_PATH)
                .willReturn(
                    WireMock.serverError(),
                ),
        )
        val client = KodeverkHttpClient(wireMockServer.baseUrl(), "srvsupstonad")
        client.hentPoststed("XXXX") shouldBe CouldNotGetKode.left()
    }

    @Test
    fun `Sjekk at vi finner kommunenavn`() {
        wireMockServer.stubFor(
            wiremockBuilder(
                KODEVERK_KOMMUNENAVN_PATH,
            )
                .willReturn(
                    WireMock.ok(resultatKommuneJson),
                ),
        )
        val client = KodeverkHttpClient(wireMockServer.baseUrl(), "srvsupstonad")
        client.hentKommunenavn("1103") shouldBe "Stavanger".right()
    }

    // language=JSON
    private val resultatKommuneJson =
        """
        {
        "betydninger": {
            "1101": [
                  {
                    "gyldigFra": "1900-01-01",
                    "gyldigTil": "9999-12-31",
                    "beskrivelser": {
                      "nb": {
                        "term": "Eigersund",
                        "tekst": "Eigersund"
                      }
                    }
                  }
                ],
            "1102": [],
            "1103": [
              {
                "gyldigFra": "1900-01-01",
                "gyldigTil": "9999-12-31",
                "beskrivelser": {
                  "nb": {
                    "term": "Stavanger",
                    "tekst": "Stavanger"
                  }
                }
              }
            ]
          }
        }
        """.trimIndent()

    // language=JSON
    private val resultatPoststedJson =
        """
            {
              "betydninger": {
                "0319": [
                  {
                    "gyldigFra": "1900-01-01",
                    "gyldigTil": "9999-12-31",
                    "beskrivelser": {
                      "nb": {
                        "term": "OSLO",
                        "tekst": "OSLO"
                      }
                    }
                  }
                ],
                "1479": [
                  {
                    "gyldigFra": "1900-01-01",
                    "gyldigTil": "9999-12-31",
                    "beskrivelser": {
                      "nb": {
                        "term": "KURLAND",
                        "tekst": "KURLAND"
                      }
                    }
                  }
                ]
              }
            }
        """.trimIndent()

    private fun wiremockBuilder(path: String) = WireMock.get(WireMock.urlPathEqualTo(path))
        .withHeader("Content-Type", WireMock.equalTo("application/json"))
        .withHeader("Nav-Call-Id", WireMock.equalTo("correlationId"))
        .withHeader("Nav-Consumer-Id", WireMock.equalTo("srvsupstonad"))
        .withQueryParam("ekskluderUgyldige", WireMock.equalTo("true"))
        .withQueryParam("spraak", WireMock.equalTo("nb"))
}
