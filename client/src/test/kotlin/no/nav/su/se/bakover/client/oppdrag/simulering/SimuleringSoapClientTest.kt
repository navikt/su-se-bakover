package no.nav.su.se.bakover.client.oppdrag.simulering

import arrow.core.left
import arrow.core.right
import com.fasterxml.jackson.module.kotlin.readValue
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.extensions.desember
import no.nav.su.se.bakover.common.extensions.januar
import no.nav.su.se.bakover.common.extensions.toNonEmptyList
import no.nav.su.se.bakover.common.infrastructure.xml.xmlMapper
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.common.tid.periode.desember
import no.nav.su.se.bakover.common.tid.periode.januar
import no.nav.su.se.bakover.common.tid.periode.oktober
import no.nav.su.se.bakover.common.tid.periode.år
import no.nav.su.se.bakover.domain.oppdrag.UtbetalingsinstruksjonForEtterbetalinger
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimulerUtbetalingForPeriode
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringFeilet
import no.nav.su.se.bakover.domain.sak.lagNyUtbetaling
import no.nav.su.se.bakover.test.beregnetRevurdering
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedLocalDate
import no.nav.su.se.bakover.test.fnr
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.saksbehandler
import no.nav.su.se.bakover.test.shouldBeType
import no.nav.system.os.eksponering.simulerfpservicewsbinding.SimulerBeregningFeilUnderBehandling
import no.nav.system.os.eksponering.simulerfpservicewsbinding.SimulerFpService
import no.nav.system.os.tjenester.simulerfpservice.feil.FeilUnderBehandling
import no.nav.system.os.tjenester.simulerfpservice.simulerfpservicegrensesnitt.SendInnOppdragRequest
import no.nav.system.os.tjenester.simulerfpservice.simulerfpservicegrensesnitt.SendInnOppdragResponse
import no.nav.system.os.tjenester.simulerfpservice.simulerfpservicegrensesnitt.SimulerBeregningRequest
import org.junit.jupiter.api.Test
import økonomi.domain.simulering.Simulering
import økonomi.domain.simulering.SimulertMåned
import java.net.SocketException
import javax.net.ssl.SSLException
import javax.xml.ws.WebServiceException

internal class SimuleringSoapClientTest {

    private val nyUtbetaling = beregnetRevurdering().let {
        it.first.lagNyUtbetaling(
            saksbehandler = saksbehandler,
            beregning = it.second.beregning,
            clock = fixedClock,
            utbetalingsinstruksjonForEtterbetaling = UtbetalingsinstruksjonForEtterbetalinger.SåFortSomMulig,
            uføregrunnlag = it.second.vilkårsvurderinger.uføreVilkår().getOrFail().grunnlag.toNonEmptyList(),
        )
    }

    @Test
    fun `should return ok simulering`() {
        val simuleringService = SimuleringSoapClient(
            simulerFpService = object : SimulerFpService {
                override fun sendInnOppdrag(parameters: SendInnOppdragRequest?): SendInnOppdragResponse {
                    throw IllegalStateException()
                }

                override fun simulerBeregning(parameters: SimulerBeregningRequest?): no.nav.system.os.tjenester.simulerfpservice.simulerfpservicegrensesnitt.SimulerBeregningResponse {
                    return xmlMapper.readValue(xmlResponse)
                }
            },
            clock = fixedClock,
        )

        simuleringService.simulerUtbetaling(
            request = SimulerUtbetalingForPeriode(
                simuleringsperiode = år(2020),
                utbetaling = nyUtbetaling,
            ),
        ).getOrFail().shouldBeType<Simulering>()
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

        simuleringService.simulerUtbetaling(
            request = SimulerUtbetalingForPeriode(
                simuleringsperiode = Periode.create(
                    fraOgMed = 1.januar(2018),
                    tilOgMed = 31.desember(2020),
                ),
                utbetaling = nyUtbetaling,
            ),
        ) shouldBe Simulering(
            gjelderId = nyUtbetaling.fnr,
            gjelderNavn = nyUtbetaling.fnr.toString(),
            nettoBeløp = 0,
            datoBeregnet = fixedLocalDate,
            måneder = SimulertMåned.create(januar(2018)..desember(2020)),
            rawResponse = "Tom respons fra oppdrag.",
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

        simuleringService.simulerUtbetaling(
            request = SimulerUtbetalingForPeriode(
                simuleringsperiode = oktober(2020)..desember(2020),
                utbetaling = nyUtbetaling,
            ),
        ) shouldBe Simulering(
            gjelderId = nyUtbetaling.fnr,
            gjelderNavn = nyUtbetaling.fnr.toString(),
            nettoBeløp = 0,
            datoBeregnet = fixedLocalDate,
            måneder = SimulertMåned.create(oktober(2020)..desember(2020)),
            rawResponse = "Tom respons fra oppdrag.",
        ).right()
    }

    //language=xml
    private val xmlResponse = """
    <simulerBeregningResponse xmlns="http://nav.no/system/os/tjenester/simulerFpService/simulerFpServiceGrensesnitt">
             <response xmlns="">
                <simulering>
                   <gjelderId>$fnr</gjelderId>
                   <gjelderNavn>navn</gjelderNavn>
                   <datoBeregnet>2521-04-07</datoBeregnet>
                   <kodeFaggruppe>INNT</kodeFaggruppe>
                   <belop>10390.00</belop>
                   <beregningsPeriode xmlns="http://nav.no/system/os/entiteter/beregningSkjema">
                      <periodeFom xmlns="">2021-04-01</periodeFom>
                      <periodeTom xmlns="">2021-04-30</periodeTom>
                      <beregningStoppnivaa>
                         <kodeFagomraade xmlns="">SUUFORE</kodeFagomraade>
                         <stoppNivaaId xmlns="">1</stoppNivaaId>
                         <behandlendeEnhet xmlns="">8020</behandlendeEnhet>
                         <oppdragsId xmlns="">53387554</oppdragsId>
                         <fagsystemId xmlns="">1234</fagsystemId>
                         <kid xmlns=""/>
                         <utbetalesTilId xmlns="">$fnr</utbetalesTilId>
                         <utbetalesTilNavn xmlns="">navn</utbetalesTilNavn>
                         <bilagsType xmlns="">U</bilagsType>
                         <forfall xmlns="">2021-04-19</forfall>
                         <feilkonto xmlns="">false</feilkonto>
                         <beregningStoppnivaaDetaljer>
                            <faktiskFom xmlns="">2021-04-01</faktiskFom>
                            <faktiskTom xmlns="">2021-04-30</faktiskTom>
                            <kontoStreng xmlns="">12345</kontoStreng>
                            <behandlingskode xmlns="">2</behandlingskode>
                            <belop xmlns="">20779.00</belop>
                            <trekkVedtakId xmlns="">0</trekkVedtakId>
                            <stonadId xmlns=""></stonadId>
                            <korrigering xmlns=""></korrigering>
                            <tilbakeforing xmlns="">false</tilbakeforing>
                            <linjeId xmlns="">3</linjeId>
                            <sats xmlns="">20779.00</sats>
                            <typeSats xmlns="">MND</typeSats>
                            <antallSats xmlns="">1.00</antallSats>
                            <saksbehId xmlns="">Z123</saksbehId>
                            <uforeGrad xmlns="">0</uforeGrad>
                            <kravhaverId xmlns=""></kravhaverId>
                            <delytelseId xmlns="">0adee7fd-f21b-4fcb-9dc0-e2234a</delytelseId>
                            <bostedsenhet xmlns="">8020</bostedsenhet>
                            <skykldnerId xmlns=""></skykldnerId>
                            <klassekode xmlns="">SUUFORE</klassekode>
                            <klasseKodeBeskrivelse xmlns="">Supplerende stønad Uføre</klasseKodeBeskrivelse>
                            <typeKlasse xmlns="">YTEL</typeKlasse>
                            <typeKlasseBeskrivelse xmlns="">Klassetype for ytelseskonti</typeKlasseBeskrivelse>
                            <refunderesOrgNr xmlns=""></refunderesOrgNr>
                         </beregningStoppnivaaDetaljer>
                         <beregningStoppnivaaDetaljer>
                            <faktiskFom xmlns="">2021-04-01</faktiskFom>
                            <faktiskTom xmlns="">2021-04-30</faktiskTom>
                            <kontoStreng xmlns="">0510000</kontoStreng>
                            <behandlingskode xmlns="">0</behandlingskode>
                            <belop xmlns="">-10389.00</belop>
                            <trekkVedtakId xmlns="">11845513</trekkVedtakId>
                            <stonadId xmlns=""></stonadId>
                            <korrigering xmlns=""></korrigering>
                            <tilbakeforing xmlns="">false</tilbakeforing>
                            <linjeId xmlns="">0</linjeId>
                            <sats xmlns="">0.00</sats>
                            <typeSats xmlns="">MND</typeSats>
                            <antallSats xmlns="">30.00</antallSats>
                            <saksbehId xmlns="">Z123</saksbehId>
                            <uforeGrad xmlns="">0</uforeGrad>
                            <kravhaverId xmlns=""></kravhaverId>
                            <delytelseId xmlns=""></delytelseId>
                            <bostedsenhet xmlns="">8020</bostedsenhet>
                            <skykldnerId xmlns=""></skykldnerId>
                            <klassekode xmlns="">FSKTSKAT</klassekode>
                            <klasseKodeBeskrivelse xmlns="">Forskuddskatt</klasseKodeBeskrivelse>
                            <typeKlasse xmlns="">SKAT</typeKlasse>
                            <typeKlasseBeskrivelse xmlns="">Klassetype for skatt</typeKlasseBeskrivelse>
                            <refunderesOrgNr xmlns=""></refunderesOrgNr>
                         </beregningStoppnivaaDetaljer>
                      </beregningStoppnivaa>
                   </beregningsPeriode>
                </simulering>
                <infomelding>
                   <beskrMelding>Simulering er utført uten skattevedtak. Nominell sats benyttet.</beskrMelding>
                </infomelding>
             </response>
          </simulerBeregningResponse>
    """.trimIndent()
}
