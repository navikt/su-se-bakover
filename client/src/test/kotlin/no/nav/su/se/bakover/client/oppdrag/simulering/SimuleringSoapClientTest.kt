package no.nav.su.se.bakover.client.oppdrag.simulering

import arrow.core.left
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.domain.oppdrag.Oppdrag
import no.nav.su.se.bakover.domain.oppdrag.Oppdragslinje
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringFeilet
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

internal class SimuleringSoapClientTest {

    @Test
    fun `should return ok simulering`() {
        val simuleringService = SimuleringSoapClient(object : SimulerFpService {
            override fun sendInnOppdrag(parameters: SendInnOppdragRequest?): SendInnOppdragResponse {
                throw IllegalStateException()
            }

            override fun simulerBeregning(parameters: SimulerBeregningRequest?): no.nav.system.os.tjenester.simulerfpservice.simulerfpservicegrensesnitt.SimulerBeregningResponse {
                return no.nav.system.os.tjenester.simulerfpservice.simulerfpservicegrensesnitt.SimulerBeregningResponse()
                    .apply { response = okSimuleringResponse() }
            }
        })

        val actual = simuleringService.simulerOppdrag(createOppdrag(), "12345678910")
        actual.isRight() shouldBe true
    }

    @Test
    fun `should handle simulering with empty response`() {
        val simuleringService = SimuleringSoapClient(object : SimulerFpService {
            override fun sendInnOppdrag(parameters: SendInnOppdragRequest?): SendInnOppdragResponse {
                throw IllegalStateException()
            }

            override fun simulerBeregning(parameters: SimulerBeregningRequest?): no.nav.system.os.tjenester.simulerfpservice.simulerfpservicegrensesnitt.SimulerBeregningResponse {
                return no.nav.system.os.tjenester.simulerfpservice.simulerfpservicegrensesnitt.SimulerBeregningResponse()
            }
        })

        val response = simuleringService.simulerOppdrag(createOppdrag(), "12345678910")
        response shouldBe SimuleringFeilet.FUNKSJONELL_FEIL.left()
    }

    @Test
    fun `should handle known error situations`() {
        val simuleringService = SimuleringSoapClient(object : SimulerFpService {
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
        })

        val response = simuleringService.simulerOppdrag(createOppdrag(), "12345678910")

        response shouldBe SimuleringFeilet.FUNKSJONELL_FEIL.left()
    }

    @Test
    fun `should handle utenfor åpningstid exception SSLException`() {
        val simuleringService = SimuleringSoapClient(object : SimulerFpService {
            override fun sendInnOppdrag(parameters: SendInnOppdragRequest?): SendInnOppdragResponse {
                throw IllegalStateException()
            }

            override fun simulerBeregning(parameters: SimulerBeregningRequest?): no.nav.system.os.tjenester.simulerfpservice.simulerfpservicegrensesnitt.SimulerBeregningResponse {
                throw WebServiceException(SSLException(""))
            }
        })

        val response = simuleringService.simulerOppdrag(createOppdrag(), "12345678910")

        response shouldBe SimuleringFeilet.OPPDRAG_UR_ER_STENGT.left()
    }

    @Test
    fun `should handle utenfor åpningstid exception SocketException`() {
        val simuleringService = SimuleringSoapClient(object : SimulerFpService {
            override fun sendInnOppdrag(parameters: SendInnOppdragRequest?): SendInnOppdragResponse {
                throw IllegalStateException()
            }

            override fun simulerBeregning(parameters: SimulerBeregningRequest?): no.nav.system.os.tjenester.simulerfpservice.simulerfpservicegrensesnitt.SimulerBeregningResponse {
                throw WebServiceException(SocketException(""))
            }
        })

        val response = simuleringService.simulerOppdrag(createOppdrag(), "12345678910")

        response shouldBe SimuleringFeilet.OPPDRAG_UR_ER_STENGT.left()
    }

    @Test
    fun `should handle unknown technical errors`() {
        val simuleringService = SimuleringSoapClient(object : SimulerFpService {
            override fun sendInnOppdrag(parameters: SendInnOppdragRequest?): SendInnOppdragResponse {
                throw IllegalStateException()
            }

            override fun simulerBeregning(parameters: SimulerBeregningRequest?): no.nav.system.os.tjenester.simulerfpservice.simulerfpservicegrensesnitt.SimulerBeregningResponse {
                throw WebServiceException(IllegalArgumentException())
            }
        })

        val response = simuleringService.simulerOppdrag(createOppdrag(), "12345678910")

        response shouldBe SimuleringFeilet.TEKNISK_FEIL.left()
    }

    private fun createOppdrag(): Oppdrag {
        val sakId = UUID.randomUUID()
        return Oppdrag(
            sakId = sakId,
            behandlingId = UUID.randomUUID(),
            oppdragslinjer = listOf(
                Oppdragslinje(
                    id = UUID.randomUUID(),
                    fom = 1.januar(2020),
                    tom = 31.desember(2020),
                    beløp = 405,
                    forrigeOppdragslinjeId = null
                )
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
