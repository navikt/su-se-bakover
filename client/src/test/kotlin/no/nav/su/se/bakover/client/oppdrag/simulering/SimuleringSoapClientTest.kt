@file:Suppress("HttpUrlsUsage")

package no.nav.su.se.bakover.client.oppdrag.simulering

import arrow.core.left
import arrow.core.right
import com.github.tomakehurst.wiremock.client.MappingBuilder
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.http.Fault
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.extensions.toNonEmptyList
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.periode.desember
import no.nav.su.se.bakover.common.tid.periode.januar
import no.nav.su.se.bakover.domain.sak.lagNyUtbetaling
import no.nav.su.se.bakover.test.auth.FakeSamlTokenProvider
import no.nav.su.se.bakover.test.beregnetRevurdering
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedLocalDate
import no.nav.su.se.bakover.test.fnr
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.saksbehandler
import no.nav.su.se.bakover.test.shouldBeType
import no.nav.su.se.bakover.test.simulering.simuleringSoapResponseUkjentFeil
import no.nav.su.se.bakover.test.wiremock.startedWireMockServerWithCorrelationId
import org.junit.jupiter.api.Test
import økonomi.domain.simulering.Simulering
import økonomi.domain.simulering.SimuleringFeilet
import økonomi.domain.simulering.SimulertMåned
import økonomi.domain.utbetaling.UtbetalingsinstruksjonForEtterbetalinger
import java.time.Clock

internal class SimuleringSoapClientTest {

    private fun nyUtbetaling(clock: Clock) = beregnetRevurdering().let {
        it.first.lagNyUtbetaling(
            saksbehandler = saksbehandler,
            beregning = it.second.beregning,
            clock = clock,
            utbetalingsinstruksjonForEtterbetaling = UtbetalingsinstruksjonForEtterbetalinger.SåFortSomMulig,
            uføregrunnlag = it.second.vilkårsvurderinger.uføreVilkår().getOrFail().grunnlag.toNonEmptyList(),
        )
    }

    @Test
    fun `should return ok simulering`() {
        val clock = fixedClock
        startedWireMockServerWithCorrelationId {
            stubFor(
                wiremockBuilder("/a").willReturn(
                    WireMock.okXml(xmlResponse),
                ),
            )
            SimuleringSoapClient(
                baseUrl = "${this.baseUrl()}/a",
                samlTokenProvider = FakeSamlTokenProvider(clock = clock, token = "a"),
                clock = clock,
            ).simulerUtbetaling(nyUtbetaling(clock)).getOrFail().shouldBeType<Simulering>()
        }
    }

    @Test
    fun `should handle simulering with empty response`() {
        // kommentar jah: Veldig usikker på om denne faktisk kan inntreffe. Veldig spesielt hvis vi får et beløp>0 uten perioder.
        val clock = fixedClock
        val utbetalingForSimulering = nyUtbetaling(clock)
        val xmlResponse = xmlUtenBeregningsperioder(utbetalingForSimulering.fnr)
        startedWireMockServerWithCorrelationId {
            stubFor(
                wiremockBuilder("/b").willReturn(
                    WireMock.okXml(xmlResponse),
                ),
            )
            SimuleringSoapClient(
                baseUrl = "${this.baseUrl()}/b",
                samlTokenProvider = FakeSamlTokenProvider(clock = clock, token = "b"),
                clock = clock,
            ).simulerUtbetaling(utbetalingForSimulering).getOrFail() shouldBe (
                Simulering(
                    gjelderId = utbetalingForSimulering.fnr,
                    gjelderNavn = "navn",
                    nettoBeløp = 10390,
                    datoBeregnet = fixedLocalDate,
                    måneder = SimulertMåned.create(januar(2021)..desember(2021)),
                    rawResponse = xmlResponse,
                )
                )
        }
    }

    @Test
    fun `should handle network error`() {
        val clock = fixedClock
        startedWireMockServerWithCorrelationId {
            stubFor(
                wiremockBuilder("/c").willReturn(
                    aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER),
                ),
            )
            SimuleringSoapClient(
                baseUrl = "${this.baseUrl()}/c",
                samlTokenProvider = FakeSamlTokenProvider(clock = clock, token = "c"),
                clock = clock,
            ).simulerUtbetaling(nyUtbetaling(clock)) shouldBe SimuleringFeilet.UtenforÅpningstid.left()
        }
    }

    @Test
    fun `cics feil`() {
        val clock = fixedClock
        startedWireMockServerWithCorrelationId {
            stubFor(
                wiremockBuilder("/d").willReturn(
                    aResponse().withStatus(500).withBody(xmlCicsFeil),
                ),
            )
            SimuleringSoapClient(
                baseUrl = "${this.baseUrl()}/d",
                samlTokenProvider = FakeSamlTokenProvider(clock = clock, token = "d"),
                clock = clock,
            ).simulerUtbetaling(nyUtbetaling(clock)) shouldBe SimuleringFeilet.TekniskFeil.left()
        }
    }

    @Test
    fun `teknisk feil`() {
        val clock = fixedClock
        startedWireMockServerWithCorrelationId {
            stubFor(
                wiremockBuilder("/d").willReturn(
                    aResponse().withStatus(500).withBody(xmlSimulerBeregningFeilUnderBehandling),
                ),
            )
            SimuleringSoapClient(
                baseUrl = "${this.baseUrl()}/d",
                samlTokenProvider = FakeSamlTokenProvider(clock = clock, token = "d"),
                clock = clock,
            ).simulerUtbetaling(nyUtbetaling(clock)) shouldBe SimuleringFeilet.TekniskFeil.left()
        }
    }

    @Test
    fun `skal returnere simulering ekvivalent med 0-utbetaling dersom response ikke inneholder data`() {
        val clock = fixedClock
        val utbetalingForSimulering = nyUtbetaling(clock)
        startedWireMockServerWithCorrelationId {
            this.stubFor(
                wiremockBuilder("/e").willReturn(
                    aResponse().withStatus(200).withBody(xmlUtenSimulering),
                ),
            )
            SimuleringSoapClient(
                baseUrl = "${this.baseUrl()}/e",
                samlTokenProvider = FakeSamlTokenProvider(clock = clock, token = "e"),
                clock = clock,
            ).simulerUtbetaling(utbetalingForSimulering) shouldBe (
                Simulering(
                    gjelderId = utbetalingForSimulering.fnr,
                    gjelderNavn = utbetalingForSimulering.fnr.toString(),
                    nettoBeløp = 0,
                    datoBeregnet = fixedLocalDate,
                    måneder = SimulertMåned.create(januar(2021)..desember(2021)),
                    rawResponse = xmlUtenSimulering,
                ).right()
                )
        }
    }

    @Test
    fun `skal returnere simulering ekvivalent med 0-utbetaling dersom response mangler`() {
        val clock = fixedClock
        val utbetalingForSimulering = nyUtbetaling(clock)
        startedWireMockServerWithCorrelationId {
            stubFor(
                wiremockBuilder("/f").willReturn(
                    aResponse().withStatus(200).withBody(xmlUtenResponse),
                ),
            )
            SimuleringSoapClient(
                baseUrl = "${this.baseUrl()}/f",
                samlTokenProvider = FakeSamlTokenProvider(clock = clock, token = "f"),
                clock = clock,
            ).simulerUtbetaling(utbetalingForSimulering) shouldBe Simulering(
                gjelderId = utbetalingForSimulering.fnr,
                gjelderNavn = utbetalingForSimulering.fnr.toString(),
                nettoBeløp = 0,
                datoBeregnet = fixedLocalDate,
                måneder = SimulertMåned.create(januar(2021)..desember(2021)),
                rawResponse = xmlUtenResponse,
            ).right()
        }
    }

    @Test
    fun `skal feile pga soap fault`() {
        val clock = fixedClock
        val utbetalingForSimulering = nyUtbetaling(clock)
        val soapResonse = simuleringSoapResponseUkjentFeil()
        startedWireMockServerWithCorrelationId {
            stubFor(
                wiremockBuilder("/g").willReturn(
                    aResponse().withStatus(500).withBody(soapResonse),
                ),
            )
            SimuleringSoapClient(
                baseUrl = "${this.baseUrl()}/g",
                samlTokenProvider = FakeSamlTokenProvider(clock = clock, token = "g"),
                clock = clock,
            ).simulerUtbetaling(utbetalingForSimulering) shouldBe SimuleringFeilet.TekniskFeil.left()
        }
    }

    //language=xml
    private val xmlCicsFeil = """
    <S:Fault xmlns="">
        <faultcode>SOAP-ENV:Server</faultcode>
        <faultstring>Conversion from SOAP failed</faultstring>
        <detail>
            <CICSFault xmlns="http://www.ibm.com/software/htp/cics/WSFault">RUTINE1 17/01/2024 08:55:44 CICS01
                ERR01 1337 XML to data transformation failed. A conversion error (OUTPUT_OVERFLOW) occurred when
                converting field maksDato for WEBSERVICE simulerFpServiceWSBinding.
            </CICSFault>
        </detail>
    </S:Fault>
    """.trimIndent()

    //language=xml
    private val xmlSimulerBeregningFeilUnderBehandling = """
        <S:Fault xmlns="">
        <faultcode>Soap:Client</faultcode>
        <faultstring>simulerBeregningFeilUnderBehandling                                             </faultstring>
        <detail>
            <sf:simulerBeregningFeilUnderBehandling xmlns:sf="http://nav.no/system/os/tjenester/oppdragService">
                <errorMessage>UTBETALES-TIL-ID er ikke utfylt</errorMessage>
                <errorSource>K231BB50 section: CA10-KON</errorSource>
                <rootCause>Kode BB50018F - SQL      - MQ</rootCause>
                <dateTimeStamp>2024-01-14T09:41:29</dateTimeStamp>
            </sf:simulerBeregningFeilUnderBehandling>
        </detail>
    </S:Fault>
    """.trimIndent()

    //language=xml
    private val xmlUtenResponse = """
        <Envelope namespace="http://schemas.xmlsoap.org/soap/envelope/">
         <Body>
         <simulerBeregningResponse xmlns="http://nav.no/system/os/tjenester/simulerFpService/simulerFpServiceGrensesnitt">
        </simulerBeregningResponse>
        </Body>
        </Envelope>
    """.trimIndent()

    //language=xml
    private val xmlUtenSimulering = """
        <Envelope namespace="http://schemas.xmlsoap.org/soap/envelope/">
         <Body>
         <simulerBeregningResponse xmlns="http://nav.no/system/os/tjenester/simulerFpService/simulerFpServiceGrensesnitt">
             <response xmlns="">
            </response>
        </simulerBeregningResponse>
        </Body>
        </Envelope>
    """.trimIndent()

    //language=xml
    private fun xmlUtenBeregningsperioder(fnr: Fnr) = """
        <Envelope namespace="http://schemas.xmlsoap.org/soap/envelope/">
         <Body>
         <simulerBeregningResponse xmlns="http://nav.no/system/os/tjenester/simulerFpService/simulerFpServiceGrensesnitt">
             <response xmlns="">
                <simulering>
                   <gjelderId>$fnr</gjelderId>
                   <gjelderNavn>navn</gjelderNavn>
                   <datoBeregnet>2021-01-01</datoBeregnet>
                   <kodeFaggruppe>INNT</kodeFaggruppe>
                   <belop>10390.00</belop>
                </simulering>
            </response>
        </simulerBeregningResponse>
        </Body>
        </Envelope>
    """.trimIndent()

    //language=xml
    private val xmlResponse = """
    <Envelope namespace="http://schemas.xmlsoap.org/soap/envelope/">
    <Body>
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
          </Body>
          </Envelope>
    """.trimIndent()
}

private fun wiremockBuilder(testUrl: String): MappingBuilder = WireMock.post(WireMock.urlPathEqualTo(testUrl)).withHeader(
    "SOAPAction",
    WireMock.equalTo("http://nav.no/system/os/tjenester/simulerFpService/simulerFpServiceGrensesnitt/simulerFpService/simulerBeregningRequest"),
)
