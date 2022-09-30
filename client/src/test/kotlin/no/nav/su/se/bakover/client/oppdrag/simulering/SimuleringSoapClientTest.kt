package no.nav.su.se.bakover.client.oppdrag.simulering

import arrow.core.left
import arrow.core.nonEmptyListOf
import arrow.core.right
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.client.oppdrag.avstemming.sakId
import no.nav.su.se.bakover.client.oppdrag.avstemming.saksnummer
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.oktober
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.periode.desember
import no.nav.su.se.bakover.common.periode.oktober
import no.nav.su.se.bakover.common.periode.år
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Sakstype
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingslinje
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemmingsnøkkel
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimulerUtbetalingForPeriode
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringFeilet
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimulertPeriode
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedLocalDate
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.utbetalingslinje
import no.nav.system.os.eksponering.simulerfpservicewsbinding.SimulerBeregningFeilUnderBehandling
import no.nav.system.os.eksponering.simulerfpservicewsbinding.SimulerFpService
import no.nav.system.os.entiteter.beregningskjema.Beregning
import no.nav.system.os.entiteter.beregningskjema.BeregningStoppnivaa
import no.nav.system.os.entiteter.beregningskjema.BeregningStoppnivaaDetaljer
import no.nav.system.os.entiteter.beregningskjema.BeregningsPeriode
import no.nav.system.os.tjenester.simulerfpservice.feil.FeilUnderBehandling
import no.nav.system.os.tjenester.simulerfpservice.simulerfpservicegrensesnitt.SendInnOppdragRequest
import no.nav.system.os.tjenester.simulerfpservice.simulerfpservicegrensesnitt.SendInnOppdragResponse
import no.nav.system.os.tjenester.simulerfpservice.simulerfpservicegrensesnitt.SimulerBeregningRequest
import no.nav.system.os.tjenester.simulerfpservice.simulerfpserviceservicetypes.SimulerBeregningResponse
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.math.BigInteger
import java.net.SocketException
import java.time.Clock
import javax.net.ssl.SSLException
import javax.xml.ws.WebServiceException

internal class SimuleringSoapClientTest {

    private val FNR = Fnr("12345678910")

    private val nyUtbetaling = createUtbetaling()

    @Test
    fun `should return ok simulering`() {
        val simuleringService = SimuleringSoapClient(
            simulerFpService = object : SimulerFpService {
                override fun sendInnOppdrag(parameters: SendInnOppdragRequest?): SendInnOppdragResponse {
                    throw IllegalStateException()
                }

                override fun simulerBeregning(parameters: SimulerBeregningRequest?): no.nav.system.os.tjenester.simulerfpservice.simulerfpservicegrensesnitt.SimulerBeregningResponse {
                    return no.nav.system.os.tjenester.simulerfpservice.simulerfpservicegrensesnitt.SimulerBeregningResponse()
                        .apply { response = okSimuleringResponse() }
                }
            },
            clock = fixedClock,
        )

        simuleringService.simulerUtbetaling(
            request = SimulerUtbetalingForPeriode(
                simuleringsperiode = år(2020),
                utbetaling = nyUtbetaling,
            ),
        ) shouldBe SimuleringResponseMapper(
            okSimuleringResponse(),
            fixedClock,
        ).simulering.right()
    }

    @Test
    fun `should handle simulering with empty response`() {
        val simuleringService = SimuleringSoapClient(
            simulerFpService = object : SimulerFpService {
                override fun sendInnOppdrag(parameters: SendInnOppdragRequest?): SendInnOppdragResponse {
                    throw IllegalStateException()
                }

                override fun simulerBeregning(parameters: SimulerBeregningRequest?): no.nav.system.os.tjenester.simulerfpservice.simulerfpservicegrensesnitt.SimulerBeregningResponse {
                    return no.nav.system.os.tjenester.simulerfpservice.simulerfpservicegrensesnitt.SimulerBeregningResponse()
                }
            },
            clock = fixedClock,
        )

        val opphør = nyUtbetaling.copy(
            utbetalingslinjer = nonEmptyListOf(
                Utbetalingslinje.Endring.Opphør(
                    utbetalingslinje = nyUtbetaling.sisteUtbetalingslinje(),
                    virkningsperiode = Periode.create(1.januar(2018), nyUtbetaling.sisteUtbetalingslinje().periode.tilOgMed),
                    clock = Clock.systemUTC(),
                ),
            ),
        )
        simuleringService.simulerUtbetaling(
            request = SimulerUtbetalingForPeriode(
                simuleringsperiode = Periode.create(
                    fraOgMed = 1.januar(2018),
                    tilOgMed = 31.desember(2020),
                ),
                utbetaling = opphør,
            ),
        ) shouldBe Simulering(
            gjelderId = FNR,
            gjelderNavn = FNR.toString(),
            nettoBeløp = 0,
            datoBeregnet = fixedLocalDate,
            periodeList = listOf(
                SimulertPeriode(
                    fraOgMed = 1.januar(2018),
                    tilOgMed = 31.desember(2020),
                    utbetaling = emptyList(),
                ),
            ),
        ).right()
    }

    @Test
    fun `should handle known error situations`() {
        val simuleringService = SimuleringSoapClient(
            simulerFpService = object : SimulerFpService {
                override fun sendInnOppdrag(parameters: SendInnOppdragRequest?): SendInnOppdragResponse {
                    throw IllegalStateException()
                }

                override fun simulerBeregning(parameters: SimulerBeregningRequest?): no.nav.system.os.tjenester.simulerfpservice.simulerfpservicegrensesnitt.SimulerBeregningResponse {
                    throw SimulerBeregningFeilUnderBehandling(
                        "Simulering feilet",
                        FeilUnderBehandling().apply {
                            errorMessage = "Detaljert feilmelding"
                        },
                    )
                }
            },
            clock = fixedClock,
        )

        simuleringService.simulerUtbetaling(
            request = SimulerUtbetalingForPeriode(
                simuleringsperiode = år(2020),
                utbetaling = nyUtbetaling,
            ),
        ) shouldBe SimuleringFeilet.FunksjonellFeil.left()
    }

    @Test
    fun `should handle utenfor åpningstid exception SSLException`() {
        val simuleringService = SimuleringSoapClient(
            simulerFpService = object : SimulerFpService {
                override fun sendInnOppdrag(parameters: SendInnOppdragRequest?): SendInnOppdragResponse {
                    throw IllegalStateException()
                }

                override fun simulerBeregning(parameters: SimulerBeregningRequest?): no.nav.system.os.tjenester.simulerfpservice.simulerfpservicegrensesnitt.SimulerBeregningResponse {
                    throw WebServiceException(SSLException(""))
                }
            },
            clock = fixedClock,
        )

        simuleringService.simulerUtbetaling(
            request = SimulerUtbetalingForPeriode(
                simuleringsperiode = år(2020),
                utbetaling = nyUtbetaling,
            ),
        ) shouldBe SimuleringFeilet.UtenforÅpningstid.left()
    }

    @Test
    fun `should handle utenfor åpningstid exception SocketException`() {
        val simuleringService = SimuleringSoapClient(
            simulerFpService = object : SimulerFpService {
                override fun sendInnOppdrag(parameters: SendInnOppdragRequest?): SendInnOppdragResponse {
                    throw IllegalStateException()
                }

                override fun simulerBeregning(parameters: SimulerBeregningRequest?): no.nav.system.os.tjenester.simulerfpservice.simulerfpservicegrensesnitt.SimulerBeregningResponse {
                    throw WebServiceException(SocketException(""))
                }
            },
            clock = fixedClock,
        )

        val response = simuleringService.simulerUtbetaling(
            request = SimulerUtbetalingForPeriode(
                simuleringsperiode = år(2020),
                utbetaling = nyUtbetaling,
            ),
        )

        response shouldBe SimuleringFeilet.UtenforÅpningstid.left()
    }

    @Test
    fun `should handle unknown technical errors`() {
        val simuleringService = SimuleringSoapClient(
            simulerFpService = object : SimulerFpService {
                override fun sendInnOppdrag(parameters: SendInnOppdragRequest?): SendInnOppdragResponse {
                    throw IllegalStateException()
                }

                override fun simulerBeregning(parameters: SimulerBeregningRequest?): no.nav.system.os.tjenester.simulerfpservice.simulerfpservicegrensesnitt.SimulerBeregningResponse {
                    throw WebServiceException(IllegalArgumentException())
                }
            },
            clock = fixedClock,
        )

        simuleringService.simulerUtbetaling(
            request = SimulerUtbetalingForPeriode(
                simuleringsperiode = år(2020),
                utbetaling = nyUtbetaling,
            ),
        ) shouldBe SimuleringFeilet.TekniskFeil.left()
    }

    @Test
    fun `skal returnere simulering ekvivalent med 0-utbetaling dersom response ikke inneholder data`() {
        val simuleringService = SimuleringSoapClient(
            simulerFpService = object : SimulerFpService {
                override fun sendInnOppdrag(parameters: SendInnOppdragRequest?): SendInnOppdragResponse {
                    throw IllegalStateException()
                }

                override fun simulerBeregning(parameters: SimulerBeregningRequest?): no.nav.system.os.tjenester.simulerfpservice.simulerfpservicegrensesnitt.SimulerBeregningResponse {
                    return no.nav.system.os.tjenester.simulerfpservice.simulerfpservicegrensesnitt.SimulerBeregningResponse()
                        .apply { response = null }
                }
            },
            clock = fixedClock,
        )

        val utenBeløp = Utbetaling.UtbetalingForSimulering(
            opprettet = fixedTidspunkt,
            sakId = sakId,
            saksnummer = saksnummer,
            fnr = FNR,
            utbetalingslinjer = nonEmptyListOf(
                utbetalingslinje(
                    periode = oktober(2020)..desember(2020),
                    beløp = 0,
                ),
            ),
            behandler = NavIdentBruker.Saksbehandler("Z123"),
            avstemmingsnøkkel = Avstemmingsnøkkel(opprettet = fixedTidspunkt),
            sakstype = Sakstype.UFØRE,
        )

        simuleringService.simulerUtbetaling(
            request = SimulerUtbetalingForPeriode(
                simuleringsperiode = oktober(2020)..desember(2020),
                utbetaling = utenBeløp,
            ),
        ) shouldBe Simulering(
            gjelderId = FNR,
            gjelderNavn = FNR.toString(),
            nettoBeløp = 0,
            datoBeregnet = fixedLocalDate,
            periodeList = listOf(
                SimulertPeriode(
                    fraOgMed = 1.oktober(2020),
                    tilOgMed = 31.desember(2020),
                    utbetaling = emptyList(),
                ),
            ),
        ).right()
    }

    private fun createUtbetaling() = Utbetaling.UtbetalingForSimulering(
        opprettet = fixedTidspunkt,
        sakId = sakId,
        saksnummer = saksnummer,
        fnr = Fnr("12345678910"),
        utbetalingslinjer = nonEmptyListOf(
            utbetalingslinje(
                periode = år(2020),
                beløp = 405,
            ),
        ),
        behandler = NavIdentBruker.Saksbehandler("Z123"),
        avstemmingsnøkkel = Avstemmingsnøkkel(opprettet = fixedTidspunkt),
        sakstype = Sakstype.UFØRE,
    )

    private fun okSimuleringResponse() = SimulerBeregningResponse().apply {
        simulering = Beregning().apply {
            gjelderId = FNR.toString()
            gjelderNavn = "gjelderNavn"
            datoBeregnet = "2020-01-01"
            belop = BigDecimal(15000)
            beregningsPeriode.add(
                BeregningsPeriode().apply {
                    periodeFom = "2020-01-01"
                    periodeTom = "2020-12-31"
                    beregningStoppnivaa.add(
                        BeregningStoppnivaa().apply {
                            fagsystemId = "SUP"
                            utbetalesTilId = FNR.toString()
                            utbetalesTilNavn = "utbetalesTilNavn"
                            forfall = "2020-02-01"
                            isFeilkonto = false
                            beregningStoppnivaaDetaljer.add(
                                BeregningStoppnivaaDetaljer().apply {
                                    faktiskFom = "2020-01-01"
                                    faktiskTom = "2020-12-31"
                                    uforeGrad = BigInteger.valueOf(50L)
                                    antallSats = BigDecimal(20L)
                                    typeSats = "4"
                                    sats = BigDecimal(400)
                                    belop = BigDecimal(15000)
                                    kontoStreng = "1234.12.12345"
                                    isTilbakeforing = false
                                    klassekode = "SUUFORE"
                                    klasseKodeBeskrivelse = "klasseKodeBeskrivelse"
                                    typeKlasse = "YTEL"
                                },
                            )
                        },
                    )
                },
            )
        }
    }
}
