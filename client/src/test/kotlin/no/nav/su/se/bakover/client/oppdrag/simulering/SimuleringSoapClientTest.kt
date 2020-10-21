package no.nav.su.se.bakover.client.oppdrag.simulering

import arrow.core.left
import arrow.core.right
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.oktober
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.oppdrag.Oppdrag
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingslinje
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemmingsnøkkel
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringFeilet
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimulertPeriode
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
import java.time.LocalDate
import java.util.UUID
import javax.net.ssl.SSLException
import javax.xml.ws.WebServiceException

internal class SimuleringSoapClientTest {

    private val FNR = Fnr("12345678910")

    private val nyUtbetaling = createUtbetaling()

    @Test
    fun `should return ok simulering`() {
        val simuleringService = SimuleringSoapClient(
            object : SimulerFpService {
                override fun sendInnOppdrag(parameters: SendInnOppdragRequest?): SendInnOppdragResponse {
                    throw IllegalStateException()
                }

                override fun simulerBeregning(parameters: SimulerBeregningRequest?): no.nav.system.os.tjenester.simulerfpservice.simulerfpservicegrensesnitt.SimulerBeregningResponse {
                    return no.nav.system.os.tjenester.simulerfpservice.simulerfpservicegrensesnitt.SimulerBeregningResponse()
                        .apply { response = okSimuleringResponse() }
                }
            }
        )

        val actual = simuleringService.simulerUtbetaling(nyUtbetaling)
        actual.isRight() shouldBe true
    }

    @Test
    fun `should handle simulering with empty response`() {
        val simuleringService = SimuleringSoapClient(
            object : SimulerFpService {
                override fun sendInnOppdrag(parameters: SendInnOppdragRequest?): SendInnOppdragResponse {
                    throw IllegalStateException()
                }

                override fun simulerBeregning(parameters: SimulerBeregningRequest?): no.nav.system.os.tjenester.simulerfpservice.simulerfpservicegrensesnitt.SimulerBeregningResponse {
                    return no.nav.system.os.tjenester.simulerfpservice.simulerfpservicegrensesnitt.SimulerBeregningResponse()
                }
            }
        )

        val response = simuleringService.simulerUtbetaling(nyUtbetaling)
        response shouldBe SimuleringFeilet.FUNKSJONELL_FEIL.left()
    }

    @Test
    fun `should handle known error situations`() {
        val simuleringService = SimuleringSoapClient(
            object : SimulerFpService {
                override fun sendInnOppdrag(parameters: SendInnOppdragRequest?): SendInnOppdragResponse {
                    throw IllegalStateException()
                }

                override fun simulerBeregning(parameters: SimulerBeregningRequest?): no.nav.system.os.tjenester.simulerfpservice.simulerfpservicegrensesnitt.SimulerBeregningResponse {
                    throw SimulerBeregningFeilUnderBehandling(
                        "Simulering feilet",
                        FeilUnderBehandling().apply {
                            errorMessage = "Detaljert feilmelding"
                        }
                    )
                }
            }
        )

        val response = simuleringService.simulerUtbetaling(nyUtbetaling)

        response shouldBe SimuleringFeilet.FUNKSJONELL_FEIL.left()
    }

    @Test
    fun `should handle utenfor åpningstid exception SSLException`() {
        val simuleringService = SimuleringSoapClient(
            object : SimulerFpService {
                override fun sendInnOppdrag(parameters: SendInnOppdragRequest?): SendInnOppdragResponse {
                    throw IllegalStateException()
                }

                override fun simulerBeregning(parameters: SimulerBeregningRequest?): no.nav.system.os.tjenester.simulerfpservice.simulerfpservicegrensesnitt.SimulerBeregningResponse {
                    throw WebServiceException(SSLException(""))
                }
            }
        )

        val response = simuleringService.simulerUtbetaling(nyUtbetaling)

        response shouldBe SimuleringFeilet.OPPDRAG_UR_ER_STENGT.left()
    }

    @Test
    fun `should handle utenfor åpningstid exception SocketException`() {
        val simuleringService = SimuleringSoapClient(
            object : SimulerFpService {
                override fun sendInnOppdrag(parameters: SendInnOppdragRequest?): SendInnOppdragResponse {
                    throw IllegalStateException()
                }

                override fun simulerBeregning(parameters: SimulerBeregningRequest?): no.nav.system.os.tjenester.simulerfpservice.simulerfpservicegrensesnitt.SimulerBeregningResponse {
                    throw WebServiceException(SocketException(""))
                }
            }
        )

        val response = simuleringService.simulerUtbetaling(nyUtbetaling)

        response shouldBe SimuleringFeilet.OPPDRAG_UR_ER_STENGT.left()
    }

    @Test
    fun `should handle unknown technical errors`() {
        val simuleringService = SimuleringSoapClient(
            object : SimulerFpService {
                override fun sendInnOppdrag(parameters: SendInnOppdragRequest?): SendInnOppdragResponse {
                    throw IllegalStateException()
                }

                override fun simulerBeregning(parameters: SimulerBeregningRequest?): no.nav.system.os.tjenester.simulerfpservice.simulerfpservicegrensesnitt.SimulerBeregningResponse {
                    throw WebServiceException(IllegalArgumentException())
                }
            }
        )

        val response = simuleringService.simulerUtbetaling(nyUtbetaling)

        response shouldBe SimuleringFeilet.TEKNISK_FEIL.left()
    }

    @Test
    fun `skal returnere simulering ekvivalent med 0-utbetaling dersom response ikke inneholder data`() {
        val simuleringService = SimuleringSoapClient(
            object : SimulerFpService {
                override fun sendInnOppdrag(parameters: SendInnOppdragRequest?): SendInnOppdragResponse {
                    throw IllegalStateException()
                }

                override fun simulerBeregning(parameters: SimulerBeregningRequest?): no.nav.system.os.tjenester.simulerfpservice.simulerfpservicegrensesnitt.SimulerBeregningResponse {
                    return no.nav.system.os.tjenester.simulerfpservice.simulerfpservicegrensesnitt.SimulerBeregningResponse()
                        .apply { response = null }
                }
            }
        )

        val utenBeløp = Utbetaling.UtbetalingForSimulering(
            fnr = FNR,
            utbetalingslinjer = listOf(
                Utbetalingslinje(
                    fraOgMed = 1.oktober(2020),
                    tilOgMed = 31.desember(2020),
                    forrigeUtbetalingslinjeId = null,
                    beløp = 0
                )
            ),
            type = Utbetaling.UtbetalingsType.NY,
            oppdragId = UUID30.randomUUID(),
            behandler = NavIdentBruker.Saksbehandler("Z123"),
            avstemmingsnøkkel = Avstemmingsnøkkel()
        )

        simuleringService.simulerUtbetaling(utenBeløp) shouldBe Simulering(
            gjelderId = FNR,
            gjelderNavn = FNR.toString(),
            nettoBeløp = 0,
            datoBeregnet = LocalDate.now(),
            periodeList = listOf(
                SimulertPeriode(
                    fraOgMed = 1.oktober(2020),
                    tilOgMed = 31.desember(2020),
                    utbetaling = emptyList()
                )
            )
        ).right()
    }

    @Test
    fun `returnerer funksjonell feil hvis man forsøker å utbetale penger, men simuleringen er tom`() {
        val simuleringService = SimuleringSoapClient(
            object : SimulerFpService {
                override fun sendInnOppdrag(parameters: SendInnOppdragRequest?): SendInnOppdragResponse {
                    throw IllegalStateException()
                }

                override fun simulerBeregning(parameters: SimulerBeregningRequest?): no.nav.system.os.tjenester.simulerfpservice.simulerfpservicegrensesnitt.SimulerBeregningResponse {
                    return no.nav.system.os.tjenester.simulerfpservice.simulerfpservicegrensesnitt.SimulerBeregningResponse()
                        .apply { response = null }
                }
            }
        )

        simuleringService.simulerUtbetaling(nyUtbetaling) shouldBe SimuleringFeilet.FUNKSJONELL_FEIL.left()
    }

    private fun createOppdrag() = Oppdrag(
        id = UUID30.randomUUID(),
        opprettet = Tidspunkt.EPOCH,
        sakId = UUID.randomUUID(),
        utbetalinger = mutableListOf()
    )

    private fun createUtbetaling() = Utbetaling.UtbetalingForSimulering(
        utbetalingslinjer = listOf(
            Utbetalingslinje(
                id = UUID30.randomUUID(),
                fraOgMed = 1.januar(2020),
                tilOgMed = 31.desember(2020),
                beløp = 405,
                forrigeUtbetalingslinjeId = null
            )
        ),
        fnr = Fnr("12345678910"),
        type = Utbetaling.UtbetalingsType.NY,
        oppdragId = UUID30.randomUUID(),
        behandler = NavIdentBruker.Saksbehandler("Z123"),
        avstemmingsnøkkel = Avstemmingsnøkkel()
    )

    private fun okSimuleringResponse() = SimulerBeregningResponse().apply {
        simulering = Beregning().apply {
            gjelderId = FNR.fnr
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
                            utbetalesTilId = FNR.fnr
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
                                    klassekode = "klasseKode"
                                    klasseKodeBeskrivelse = "klasseKodeBeskrivelse"
                                    typeKlasse = "YTEL"
                                }
                            )
                        }
                    )
                }
            )
        }
    }
}
