package no.nav.su.se.bakover.client.oppdrag.simulering

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.su.se.bakover.client.oppdrag.Utbetalingslinjer
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.januar
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
import java.util.UUID
import javax.net.ssl.SSLException
import javax.xml.ws.WebServiceException

internal class SimuleringServiceTest {

    @Test
    fun `should return ok simulering`() {
        val simuleringService = SimuleringService(object : SimulerFpService {
            override fun sendInnOppdrag(parameters: SendInnOppdragRequest?): SendInnOppdragResponse {
                TODO("Not yet implemented")
            }

            override fun simulerBeregning(parameters: SimulerBeregningRequest?): no.nav.system.os.tjenester.simulerfpservice.simulerfpservicegrensesnitt.SimulerBeregningResponse {
                return no.nav.system.os.tjenester.simulerfpservice.simulerfpservicegrensesnitt.SimulerBeregningResponse()
                    .apply { response = okSimuleringResponse() }
            }
        })

        val response = simuleringService.simulerOppdrag(createUtbetalingslinjer())

        response.status shouldBe SimuleringStatus.OK
        response.simulering shouldNotBe null
        response.feilmelding shouldBe null
    }

    @Test
    fun `should handle simulering with empty response`() {
        val simuleringService = SimuleringService(object : SimulerFpService {
            override fun sendInnOppdrag(parameters: SendInnOppdragRequest?): SendInnOppdragResponse {
                TODO("Not yet implemented")
            }

            override fun simulerBeregning(parameters: SimulerBeregningRequest?): no.nav.system.os.tjenester.simulerfpservice.simulerfpservicegrensesnitt.SimulerBeregningResponse {
                return no.nav.system.os.tjenester.simulerfpservice.simulerfpservicegrensesnitt.SimulerBeregningResponse()
            }
        })

        val response = simuleringService.simulerOppdrag(createUtbetalingslinjer())

        response.status shouldBe SimuleringStatus.FUNKSJONELL_FEIL
        response.simulering shouldBe null
        response.feilmelding shouldBe "Fikk ingen respons"
    }

    @Test
    fun `should handle known error situations`() {
        val simuleringService = SimuleringService(object : SimulerFpService {
            override fun sendInnOppdrag(parameters: SendInnOppdragRequest?): SendInnOppdragResponse {
                TODO("Not yet implemented")
            }

            override fun simulerBeregning(parameters: SimulerBeregningRequest?): no.nav.system.os.tjenester.simulerfpservice.simulerfpservicegrensesnitt.SimulerBeregningResponse {
                throw SimulerBeregningFeilUnderBehandling("Simulering feilet", FeilUnderBehandling().apply {
                    errorMessage = "Detaljert feilmelding"
                })
            }
        })

        val response = simuleringService.simulerOppdrag(createUtbetalingslinjer())

        response.status shouldBe SimuleringStatus.FUNKSJONELL_FEIL
        response.simulering shouldBe null
        response.feilmelding shouldBe "Detaljert feilmelding"
    }

    @Test
    fun `should handle utenfor åpningstid exception SSLException`() {
        val simuleringService = SimuleringService(object : SimulerFpService {
            override fun sendInnOppdrag(parameters: SendInnOppdragRequest?): SendInnOppdragResponse {
                TODO("Not yet implemented")
            }

            override fun simulerBeregning(parameters: SimulerBeregningRequest?): no.nav.system.os.tjenester.simulerfpservice.simulerfpservicegrensesnitt.SimulerBeregningResponse {
                throw WebServiceException(SSLException(""))
            }
        })

        val response = simuleringService.simulerOppdrag(createUtbetalingslinjer())

        response.status shouldBe SimuleringStatus.OPPDRAG_UR_ER_STENGT
        response.simulering shouldBe null
        response.feilmelding shouldBe "Oppdrag/UR er stengt"
    }

    @Test
    fun `should handle utenfor åpningstid exception SocketException`() {
        val simuleringService = SimuleringService(object : SimulerFpService {
            override fun sendInnOppdrag(parameters: SendInnOppdragRequest?): SendInnOppdragResponse {
                TODO("Not yet implemented")
            }

            override fun simulerBeregning(parameters: SimulerBeregningRequest?): no.nav.system.os.tjenester.simulerfpservice.simulerfpservicegrensesnitt.SimulerBeregningResponse {
                throw WebServiceException(SocketException(""))
            }
        })

        val response = simuleringService.simulerOppdrag(createUtbetalingslinjer())

        response.status shouldBe SimuleringStatus.OPPDRAG_UR_ER_STENGT
        response.simulering shouldBe null
        response.feilmelding shouldBe "Oppdrag/UR er stengt"
    }

    @Test
    fun `should handle unknown technical errors`() {
        val simuleringService = SimuleringService(object : SimulerFpService {
            override fun sendInnOppdrag(parameters: SendInnOppdragRequest?): SendInnOppdragResponse {
                TODO("Not yet implemented")
            }

            override fun simulerBeregning(parameters: SimulerBeregningRequest?): no.nav.system.os.tjenester.simulerfpservice.simulerfpservicegrensesnitt.SimulerBeregningResponse {
                throw WebServiceException(IllegalArgumentException())
            }
        })

        val response = simuleringService.simulerOppdrag(createUtbetalingslinjer())

        response.status shouldBe SimuleringStatus.TEKNISK_FEIL
        response.simulering shouldBe null
        response.feilmelding shouldBe "Fikk teknisk feil ved simulering"
    }

    private fun createUtbetalingslinjer() = Utbetalingslinjer(
        fagområde = "Fagområde",
        fagsystemId = "SUP",
        fødselsnummer = "12345678910",
        endringskode = "NY",
        saksbehandler = "saksbehandler"
    ).also {
        it.linje(
            Utbetalingslinjer.Utbetalingslinje(
                delytelseId = UUID.randomUUID().toString(),
                endringskode = "NY",
                klassekode = "klasseKode",
                fom = 1.januar(2020),
                tom = 31.desember(2020),
                dagsats = 405,
                refDelytelseId = UUID.randomUUID().toString(),
                refFagsystemId = "SUP",
                datoStatusFom = 1.januar(2020),
                statuskode = null
            )
        )
    }

    private fun okSimuleringResponse() = SimulerBeregningResponse().apply {
        simulering = Beregning().apply {
            gjelderId = "gjelderId"
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
                            utbetalesTilId = "utbetalesTilId"
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
                                    typeKlasse = "typeKlasse"
                                    refunderesOrgNr = "123456789"
                                }
                            )
                        }
                    )
                }
            )
        }
    }
}
