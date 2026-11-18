package no.nav.su.se.bakover.client.simulering

import arrow.core.Either
import arrow.core.nonEmptyListOf
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.containing
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.equalToXml
import com.github.tomakehurst.wiremock.client.WireMock.matching
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.client.oppdrag.avstemming.sakId
import no.nav.su.se.bakover.client.oppdrag.avstemming.saksnummer
import no.nav.su.se.bakover.client.oppdrag.simulering.SimuleringErrorCode
import no.nav.su.se.bakover.client.oppdrag.simulering.SimuleringErrorDto
import no.nav.su.se.bakover.client.oppdrag.simulering.SimuleringProxyClientGcp
import no.nav.su.se.bakover.client.oppdrag.simulering.buildXmlRequestBody
import no.nav.su.se.bakover.client.oppdrag.simulering.historisk.HistoriskErstatningslinjevalg
import no.nav.su.se.bakover.client.oppdrag.simulering.historisk.UverifisertHistoriskOppdragsformat
import no.nav.su.se.bakover.client.oppdrag.simulering.historisk.UverifisertInfotrygdOpphørslinje
import no.nav.su.se.bakover.client.oppdrag.simulering.historisk.byggHistoriskInfotrygdOppdragRequest
import no.nav.su.se.bakover.client.oppdrag.utbetaling.UtbetalingRequest
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.auth.AzureAd
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.domain.tid.januar
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.infrastructure.config.ApplicationConfig.ClientsConfig.SuProxyConfig
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.common.tid.periode.januar
import no.nav.su.se.bakover.domain.historisk.revurdering.utbetaling.HistoriskInfotrygdOppdragsgrunnlag
import no.nav.su.se.bakover.domain.oppdrag.avstemming.toFagområde
import no.nav.su.se.bakover.test.TikkendeKlokke
import no.nav.su.se.bakover.test.fixedClockAt
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.historisk.HistoriskInfotrygdOppdragTestdata
import no.nav.su.se.bakover.test.simulering.SimuleringResponseData.Companion.simuleringXml
import no.nav.su.se.bakover.test.utbetaling.utbetalingslinjeNy
import no.nav.su.se.bakover.test.wiremock.startedWireMockServerWithCorrelationId
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import økonomi.domain.avstemming.Avstemmingsnøkkel
import økonomi.domain.simulering.SimuleringFeilet
import økonomi.domain.utbetaling.Utbetaling
import java.math.BigDecimal

class SimuleringProxyClientGcpTest {
    private val oboToken = "obo-token"
    private val systemToken = "system-token"

    private fun mockAzureAd() = mock<AzureAd> {
        on { onBehalfOfToken(any(), any()) } doReturn oboToken
        on { getSystemToken(any()) } doReturn systemToken
    }
    val clientId = "proxyclient"
    private fun createClient(baseUrl: String, azureAd: AzureAd = mockAzureAd()): SimuleringProxyClientGcp {
        val config = SuProxyConfig(url = baseUrl, clientId = clientId)
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

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun `historisk simulering bruker samme transport og beholder alle oppdrag og detaljer`(medBrukertoken: Boolean) {
        val brukertoken = if (medBrukertoken) "historisk-brukertoken" else null
        val forventetToken = if (medBrukertoken) oboToken else systemToken
        val request = historiskRequest()
        val data = HistoriskInfotrygdOppdragTestdata
        val simuleringsperiode = data.januar..data.februar.plusMonths(1)
        val egetFagsystemId = saksnummer.toString()
        val fagområde = Sakstype.ALDER.toFagområde().toString()
        val gammeltFagsystemId = "gammelt-infotrygd-oppdrag"
        val ukjentFagområde = "UKJENT"
        val ukjentKlassekode = "UKJENT_KLASSE"
        val ukjentKlassetype = "UKJENT_TYPE"
        val rawResponse = simuleringXml {
            periode {
                periodeFom = data.februar.fraOgMed.toString()
                periodeTom = data.februar.tilOgMed.toString()
                stoppnivå {
                    fagsystemId = egetFagsystemId
                    kodeFagomraade = fagområde
                    ordinær(1_000)
                }
                stoppnivå {
                    fagsystemId = egetFagsystemId
                    kodeFagomraade = fagområde
                    ordinær(2_000)
                }
                stoppnivå {
                    fagsystemId = gammeltFagsystemId
                    kodeFagomraade = ukjentFagområde
                    ordinær(3_000, klassekode = ukjentKlassekode, typeKlasse = ukjentKlassetype)
                }
            }
        }

        startedWireMockServerWithCorrelationId(token = brukertoken) {
            stubFor(
                post(urlPathEqualTo("/simulerberegning"))
                    .withHeader("Authorization", equalTo("Bearer $forventetToken"))
                    .withHeader("Content-Type", containing("application/xml"))
                    .withRequestBody(equalToXml(buildXmlRequestBody(simuleringsperiode, request)))
                    .willReturn(aResponse().withStatus(200).withBody(rawResponse)),
            )
            val azureAd = mockAzureAd()

            val svar = createClient(baseUrl(), azureAd).simulerHistoriskUtbetaling(request, simuleringsperiode)
                .shouldBeRight()

            svar.råRespons shouldBe rawResponse
            val beregning = requireNotNull(svar.respons.response?.simulering)
            val oppdrag = beregning.beregningsPeriode.single().beregningStoppnivaa
            oppdrag.map { it.fagsystemId } shouldBe listOf(egetFagsystemId, egetFagsystemId, gammeltFagsystemId)
            oppdrag.map { it.kodeFagomraade } shouldBe listOf(fagområde, fagområde, ukjentFagområde)
            oppdrag.last().beregningStoppnivaaDetaljer.single().let {
                it.klassekode shouldBe ukjentKlassekode
                it.typeKlasse shouldBe ukjentKlassetype
            }
            if (medBrukertoken) {
                org.mockito.kotlin.verify(azureAd).onBehalfOfToken(requireNotNull(brukertoken), clientId)
                org.mockito.kotlin.verify(azureAd, times(0)).getSystemToken(any())
            } else {
                org.mockito.kotlin.verify(azureAd).getSystemToken(clientId)
                org.mockito.kotlin.verify(azureAd, times(0)).onBehalfOfToken(any(), any())
            }
            this.verify(1, postRequestedFor(urlPathEqualTo("/simulerberegning")))
        }
    }

    @Test
    fun `historisk rent opphør kan sendes og svar uten beregning beholder infomeldingen`() {
        val request = historiskRequest(opphør = true)
        val periode = HistoriskInfotrygdOppdragTestdata.periode
        val infomelding = "Ingen beregning"
        val rawResponse = """
            <Envelope xmlns="http://schemas.xmlsoap.org/soap/envelope/">
                <Body>
                    <simulerBeregningResponse>
                        <response>
                            <infomelding><beskrMelding>$infomelding</beskrMelding></infomelding>
                        </response>
                    </simulerBeregningResponse>
                </Body>
            </Envelope>
        """.trimIndent()
        startedWireMockServerWithCorrelationId {
            stubFor(
                post(urlPathEqualTo("/simulerberegning"))
                    .withRequestBody(equalToXml(buildXmlRequestBody(periode, request)))
                    .willReturn(aResponse().withStatus(200).withBody(rawResponse)),
            )

            val svar = createClient(baseUrl()).simulerHistoriskUtbetaling(request, periode).shouldBeRight()

            val respons = requireNotNull(svar.respons.response)
            respons.simulering shouldBe null
            requireNotNull(respons.infomelding).beskrMelding shouldBe infomelding
            svar.råRespons shouldBe rawResponse
        }
    }

    @ParameterizedTest
    @ValueSource(ints = [500, 503])
    fun `historisk simulering gjenbruker feilhåndteringen fra proxyen`(statusCode: Int) {
        startedWireMockServerWithCorrelationId {
            stubFor(
                post(urlPathEqualTo("/simulerberegning"))
                    .willReturn(
                        aResponse().withStatus(statusCode).withBody(
                            serialize(SimuleringErrorDto(SimuleringErrorCode.FUNKSJONELL_FEIL)),
                        ),
                    ),
            )

            val resultat = createClient(baseUrl()).simulerHistoriskUtbetaling(
                historiskRequest(),
                HistoriskInfotrygdOppdragTestdata.periode,
            )

            resultat.shouldBeLeft() shouldBe if (statusCode == 500) {
                SimuleringFeilet.FunksjonellFeil
            } else {
                SimuleringFeilet.TekniskFeil
            }
        }
    }

    @Test
    fun `ugyldig historisk simuleringssvar gir teknisk feil selv om HTTP-status er 200`() {
        startedWireMockServerWithCorrelationId {
            stubFor(
                post(urlPathEqualTo("/simulerberegning"))
                    .willReturn(aResponse().withStatus(200).withBody("ikke-xml")),
            )

            createClient(baseUrl()).simulerHistoriskUtbetaling(
                historiskRequest(),
                HistoriskInfotrygdOppdragTestdata.periode,
            ).shouldBeLeft() shouldBe SimuleringFeilet.TekniskFeil
        }
    }

    private fun historiskRequest(opphør: Boolean = false): UtbetalingRequest.OppdragRequest {
        val data = HistoriskInfotrygdOppdragTestdata
        val gjeldende = data.gjeldende()
        val grunnlag = HistoriskInfotrygdOppdragsgrunnlag.bygg(
            data.revurdering(gjeldende, data.beregning(opphør = opphør)),
            gjeldende,
        ).shouldBeRight()
        return byggHistoriskInfotrygdOppdragRequest(
            grunnlag = grunnlag,
            saksnummer = saksnummer,
            fnr = utbetaling.fnr,
            utbetalingId = UUID30.randomUUID(),
            attestant = NavIdentBruker.Attestant("A123456"),
            avstemmingsnøkkel = Avstemmingsnøkkel(data.tidspunkt),
            kjøreplan = UtbetalingRequest.Oppdragslinje.Kjøreplan.NEI,
            harEksisterendeSuOppdrag = false,
            format = UverifisertHistoriskOppdragsformat(
                kodeEndring = UtbetalingRequest.KodeEndring.NY,
                opphørslinjer = data.periode.måneder().associateWith { måned ->
                    UverifisertInfotrygdOpphørslinje(
                        id = UUID30.randomUUID(),
                        kodeEndringLinje = UtbetalingRequest.Oppdragslinje.KodeEndringLinje.ENDRING,
                        vedtaksperiode = måned,
                        sats = BigDecimal.ZERO,
                    )
                },
                erstatningslinjer = if (opphør) {
                    emptyMap()
                } else {
                    data.periode.måneder().associateWith { HistoriskErstatningslinjevalg(UUID30.randomUUID(), null) }
                },
            ),
        ).shouldBeRight()
    }

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
    fun `Kan kalle simulering proxy og deserialisere svar med OBO token`() {
        val brukertoken = "usertoken"
        startedWireMockServerWithCorrelationId(token = brukertoken) {
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

            val azureAd = mockAzureAd()
            val result = createClient(baseUrl(), azureAd = azureAd).simulerUtbetaling(utbetaling)
            result.shouldBeRight()
            org.mockito.kotlin.verify(azureAd, times(1)).onBehalfOfToken(brukertoken, clientId)
            org.mockito.kotlin.verify(azureAd, times(0)).getSystemToken(any())
        }
    }

    @Test
    fun `Kan kalle simulering proxy og deserialisere svar med Systemtoken token`() {
        startedWireMockServerWithCorrelationId(token = null) {
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

            val azureAd = mockAzureAd()
            val result = createClient(baseUrl(), azureAd = azureAd).simulerUtbetaling(utbetaling)
            result.shouldBeRight()
            org.mockito.kotlin.verify(azureAd, times(0)).onBehalfOfToken(any(), any())
            org.mockito.kotlin.verify(azureAd, times(1)).getSystemToken(clientId)
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
