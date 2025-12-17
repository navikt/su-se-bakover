package no.nav.su.se.bakover.client.simulering

import arrow.core.Either
import arrow.core.nonEmptyListOf
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.containing
import com.github.tomakehurst.wiremock.client.WireMock.matching
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.client.oppdrag.avstemming.sakId
import no.nav.su.se.bakover.client.oppdrag.avstemming.saksnummer
import no.nav.su.se.bakover.client.oppdrag.simulering.SimuleringErrorCode
import no.nav.su.se.bakover.client.oppdrag.simulering.SimuleringProxyClientGcp
import no.nav.su.se.bakover.common.auth.AzureAd
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.domain.tid.januar
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.infrastructure.config.ApplicationConfig.ClientsConfig.SuProxyConfig
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.periode.januar
import no.nav.su.se.bakover.test.TikkendeKlokke
import no.nav.su.se.bakover.test.fixedClockAt
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.utbetaling.utbetalingslinjeNy
import no.nav.su.se.bakover.test.wiremock.startedWireMockServerWithCorrelationId
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import økonomi.domain.avstemming.Avstemmingsnøkkel
import økonomi.domain.simulering.SimuleringFeilet
import økonomi.domain.utbetaling.Utbetaling

class SimuleringProxyClientGcpTest {

    private fun mockAzureAd() = mock<AzureAd> {
        on { onBehalfOfToken(any(), any()) } doReturn "obo-token"
        on { getSystemToken(any()) } doReturn "system-token"
    }

    private fun createClient(baseUrl: String, azureAd: AzureAd = mockAzureAd()): SimuleringProxyClientGcp {
        val config = SuProxyConfig(url = baseUrl, clientId = "proxyclient")
        val clock = TikkendeKlokke(fixedClockAt(1.januar(2022)))
        return SimuleringProxyClientGcp(
            azureAd = azureAd,
            config = config,
            clock = clock,
        )
    }

    val utbetaling = Utbetaling.UtbetalingForSimulering(
        opprettet = fixedTidspunkt,
        sakId = sakId,
        saksnummer = saksnummer,
        fnr = Fnr("12345678910"),
        utbetalingslinjer = nonEmptyListOf(
            utbetalingslinjeNy(
                periode = januar(2021),
                beløp = 5000,
            ),
        ),
        behandler = NavIdentBruker.Saksbehandler("Z123"),
        avstemmingsnøkkel = Avstemmingsnøkkel(fixedTidspunkt),
        sakstype = Sakstype.UFØRE,
    )

    private fun validSimuleringResponse(): String =
        """
    <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/">
      <soapenv:Body>
        <simulerBeregningResponse>
          <simuleringsResultat>
            <simuleringsPeriode>
              <datoSimulerFom>2021-01-01</datoSimulerFom>
              <datoSimulerTom>2021-01-31</datoSimulerTom>
            </simuleringsPeriode>
            <utbetalingsPeriode>
              <periode>
                <fom>2021-01-01</fom>
                <tom>2021-01-31</tom>
              </periode>
              <belop>0</belop>
            </utbetalingsPeriode>
          </simuleringsResultat>
        </simulerBeregningResponse>
      </soapenv:Body>
    </soapenv:Envelope>
        """.trimIndent()

    @Test
    fun `Kan kalle simulering proxy og deserialisere svar`() {
        startedWireMockServerWithCorrelationId {
            stubFor(
                post(urlPathEqualTo("/simulerberegning"))
                    .withHeader("Authorization", matching("Bearer .*"))
                    .withHeader("Content-Type", containing("application/xml"))
                    .willReturn(
                        aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody(validSimuleringResponse()),
                    ),
            )

            val result = createClient(baseUrl(), azureAd = mockAzureAd()).simulerUtbetaling(utbetaling)
            result.shouldBeRight()
        }
    }

    @Test
    fun `500 UTENFOR_APNINGSTID mapper korrekt`() {
        startedWireMockServerWithCorrelationId {
            stubFor(
                post(urlEqualTo("/simulerberegning"))
                    .willReturn(
                        aResponse()
                            .withStatus(500)
                            .withHeader("Content-Type", "application/json")
                            .withBody(
                                """
                            {
                              "code": "${SimuleringErrorCode.UTENFOR_APNINGSTID}"
                            }
                                """.trimIndent(),
                            ),
                    ),
            )

            val result = createClient(baseUrl()).simulerUtbetaling(utbetaling)

            result shouldBe Either.Left(SimuleringFeilet.UtenforÅpningstid)
        }
    }

    @Test
    fun `500 FUNKSJONELL_FEIL mapper korrekt`() {
        startedWireMockServerWithCorrelationId {
            stubFor(
                post(urlEqualTo("/simulerberegning"))
                    .willReturn(
                        aResponse()
                            .withStatus(500)
                            .withHeader("Content-Type", "application/json")
                            .withBody(
                                """
                            {
                              "code": "${SimuleringErrorCode.FUNKSJONELL_FEIL}"
                            }
                                """.trimIndent(),
                            ),
                    ),
            )

            val result = createClient(baseUrl()).simulerUtbetaling(utbetaling)

            result shouldBe Either.Left(SimuleringFeilet.FunksjonellFeil)
        }
    }

    @Test
    fun `500 med ugyldig body gir TekniskFeil`() {
        startedWireMockServerWithCorrelationId {
            stubFor(
                post(urlEqualTo("/simulerberegning"))
                    .willReturn(
                        aResponse()
                            .withStatus(500)
                            .withHeader("Content-Type", "application/json")
                            .withBody("not-json-at-all"),
                    ),
            )

            val result = createClient(baseUrl()).simulerUtbetaling(utbetaling)

            result shouldBe Either.Left(SimuleringFeilet.TekniskFeil)
        }
    }
}
