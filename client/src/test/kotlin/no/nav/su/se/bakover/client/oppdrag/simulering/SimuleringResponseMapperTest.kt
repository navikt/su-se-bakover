package no.nav.su.se.bakover.client.oppdrag.simulering

import arrow.core.nonEmptyListOf
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.client.oppdrag.XmlMapper
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.april
import no.nav.su.se.bakover.common.februar
import no.nav.su.se.bakover.common.mai
import no.nav.su.se.bakover.common.mars
import no.nav.su.se.bakover.common.oktober
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.Sakstype
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingslinje
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemmingsnøkkel
import no.nav.su.se.bakover.domain.oppdrag.simulering.KlasseKode
import no.nav.su.se.bakover.domain.oppdrag.simulering.KlasseType
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimulertDetaljer
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimulertPeriode
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimulertUtbetaling
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedLocalDate
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.utbetalingslinje
import no.nav.system.os.tjenester.simulerfpservice.simulerfpserviceservicetypes.SimulerBeregningRequest
import org.junit.jupiter.api.Test
import java.time.Clock
import java.util.UUID
import no.nav.system.os.tjenester.simulerfpservice.simulerfpservicegrensesnitt.SimulerBeregningResponse as GrensesnittResponse

internal class SimuleringResponseMapperTest {

    private val fagsystemId = "2100"
    private val fnr = Fnr("12345678910")
    private val navn = "SNERK RAKRYGGET"
    private val konto = "123.123.123"
    private val typeSats = "MND"
    private val suBeskrivelse = "Supplerende stønad Uføre"

    @Test
    fun `mapper utbetaling og simuleringsperiode til simulering`() {
        val utbetaling = Utbetaling.UtbetalingForSimulering(
            id = UUID30.randomUUID(),
            opprettet = fixedTidspunkt,
            sakId = UUID.randomUUID(),
            saksnummer = Saksnummer(9999),
            fnr = fnr,
            utbetalingslinjer = nonEmptyListOf(
                Utbetalingslinje.Endring.Opphør(
                    utbetalingslinje = utbetalingslinje(),
                    virkningstidspunkt = 1.mai(2021),
                    clock = Clock.systemUTC(),
                ),
            ),
            type = Utbetaling.UtbetalingsType.OPPHØR,
            behandler = NavIdentBruker.Saksbehandler("saksa"),
            avstemmingsnøkkel = Avstemmingsnøkkel(opprettet = fixedTidspunkt),
            sakstype = Sakstype.UFØRE,
        )
        val simuleringsperiode = SimulerBeregningRequest.SimuleringsPeriode().apply {
            datoSimulerFom = "2021-02-01"
            datoSimulerTom = "2021-10-31"
        }

        SimuleringResponseMapper(utbetaling, simuleringsperiode, fixedClock).simulering shouldBe Simulering(
            gjelderId = fnr,
            gjelderNavn = fnr.toString(),
            datoBeregnet = fixedLocalDate,
            nettoBeløp = 0,
            periodeList = listOf(
                SimulertPeriode(
                    fraOgMed = 1.februar(2021),
                    tilOgMed = 31.oktober(2021),
                    utbetaling = emptyList(),
                ),
            ),
        )
    }

    @Test
    fun `mapper fremtidige simulerte utbetalinger`() {
        val responseMedFremtidigUtbetaling =
            XmlMapper.readValue(xmlResponseMedFremtidigUtbetaling, GrensesnittResponse::class.java).response
        SimuleringResponseMapper(responseMedFremtidigUtbetaling, fixedClock).simulering shouldBe Simulering(
            gjelderId = fnr,
            gjelderNavn = navn,
            datoBeregnet = 14.april(2021),
            nettoBeløp = 10390,
            periodeList = listOf(
                SimulertPeriode(
                    fraOgMed = 1.april(2021),
                    tilOgMed = 30.april(2021),
                    utbetaling = listOf(
                        SimulertUtbetaling(
                            fagSystemId = fagsystemId,
                            utbetalesTilId = fnr,
                            utbetalesTilNavn = navn,
                            forfall = 19.april(2021),
                            feilkonto = false,
                            detaljer = listOf(
                                SimulertDetaljer(
                                    faktiskFraOgMed = 1.april(2021),
                                    faktiskTilOgMed = 30.april(2021),
                                    konto = konto,
                                    belop = 20779,
                                    tilbakeforing = false,
                                    sats = 20779,
                                    typeSats = typeSats,
                                    antallSats = 1,
                                    uforegrad = 0,
                                    klassekode = KlasseKode.SUUFORE,
                                    klassekodeBeskrivelse = suBeskrivelse,
                                    klasseType = KlasseType.YTEL,
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )
    }

    @Test
    fun `mapper fremtidige simulerte utbetalinger - alder`() {
        val responseMedFremtidigUtbetaling =
            XmlMapper.readValue(
                xmlResponseMedFremtidigUtbetaling.replace("SUUFORE", "SUALDER"),
                GrensesnittResponse::class.java,
            ).response
        SimuleringResponseMapper(responseMedFremtidigUtbetaling, fixedClock).simulering shouldBe Simulering(
            gjelderId = fnr,
            gjelderNavn = navn,
            datoBeregnet = 14.april(2021),
            nettoBeløp = 10390,
            periodeList = listOf(
                SimulertPeriode(
                    fraOgMed = 1.april(2021),
                    tilOgMed = 30.april(2021),
                    utbetaling = listOf(
                        SimulertUtbetaling(
                            fagSystemId = fagsystemId,
                            utbetalesTilId = fnr,
                            utbetalesTilNavn = navn,
                            forfall = 19.april(2021),
                            feilkonto = false,
                            detaljer = listOf(
                                SimulertDetaljer(
                                    faktiskFraOgMed = 1.april(2021),
                                    faktiskTilOgMed = 30.april(2021),
                                    konto = konto,
                                    belop = 20779,
                                    tilbakeforing = false,
                                    sats = 20779,
                                    typeSats = typeSats,
                                    antallSats = 1,
                                    uforegrad = 0,
                                    klassekode = KlasseKode.SUALDER,
                                    klassekodeBeskrivelse = suBeskrivelse,
                                    klasseType = KlasseType.YTEL,
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )
    }

    //language=xml
    private val xmlResponseMedFremtidigUtbetaling = """
    <simulerBeregningResponse xmlns="http://nav.no/system/os/tjenester/simulerFpService/simulerFpServiceGrensesnitt">
             <response xmlns="">
                <simulering>
                   <gjelderId>$fnr</gjelderId>
                   <gjelderNavn>$navn</gjelderNavn>
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
                         <fagsystemId xmlns="">$fagsystemId</fagsystemId>
                         <kid xmlns=""/>
                         <utbetalesTilId xmlns="">$fnr</utbetalesTilId>
                         <utbetalesTilNavn xmlns="">$navn</utbetalesTilNavn>
                         <bilagsType xmlns="">U</bilagsType>
                         <forfall xmlns="">2021-04-19</forfall>
                         <feilkonto xmlns="">false</feilkonto>
                         <beregningStoppnivaaDetaljer>
                            <faktiskFom xmlns="">2021-04-01</faktiskFom>
                            <faktiskTom xmlns="">2021-04-30</faktiskTom>
                            <kontoStreng xmlns="">$konto</kontoStreng>
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

    @Test
    fun `mapper simulerte feilutbetalinger`() {
        val responseMedFeilutbetaling =
            XmlMapper.readValue(xmlResponseMedFeilutbetaling, GrensesnittResponse::class.java).response
        SimuleringResponseMapper(responseMedFeilutbetaling, fixedClock).simulering shouldBe Simulering(
            gjelderId = fnr,
            gjelderNavn = navn,
            datoBeregnet = 14.april(2021),
            nettoBeløp = 5000,
            periodeList = listOf(
                SimulertPeriode(
                    fraOgMed = 1.februar(2021),
                    tilOgMed = 28.februar(2021),
                    utbetaling = listOf(
                        SimulertUtbetaling(
                            fagSystemId = fagsystemId,
                            utbetalesTilId = fnr,
                            utbetalesTilNavn = navn,
                            forfall = 14.april(2021),
                            feilkonto = false,
                            detaljer = listOf(
                                SimulertDetaljer(
                                    faktiskFraOgMed = 1.februar(2021),
                                    faktiskTilOgMed = 28.februar(2021),
                                    konto = konto,
                                    belop = 10779,
                                    tilbakeforing = false,
                                    sats = 0,
                                    typeSats = "",
                                    antallSats = 0,
                                    uforegrad = 0,
                                    klassekode = KlasseKode.SUUFORE,
                                    klassekodeBeskrivelse = suBeskrivelse,
                                    klasseType = KlasseType.YTEL,
                                ),
                                SimulertDetaljer(
                                    faktiskFraOgMed = 1.februar(2021),
                                    faktiskTilOgMed = 28.februar(2021),
                                    konto = konto,
                                    belop = 10000,
                                    tilbakeforing = false,
                                    sats = 10000,
                                    typeSats = typeSats,
                                    antallSats = 1,
                                    uforegrad = 0,
                                    klassekode = KlasseKode.SUUFORE,
                                    klassekodeBeskrivelse = suBeskrivelse,
                                    klasseType = KlasseType.YTEL,
                                ),
                                SimulertDetaljer(
                                    faktiskFraOgMed = 1.februar(2021),
                                    faktiskTilOgMed = 28.februar(2021),
                                    konto = konto,
                                    belop = 10779,
                                    tilbakeforing = false,
                                    sats = 0,
                                    typeSats = "",
                                    antallSats = 0,
                                    uforegrad = 0,
                                    klassekode = KlasseKode.KL_KODE_FEIL_INNT,
                                    klassekodeBeskrivelse = "Feilutbetaling Inntektsytelser",
                                    klasseType = KlasseType.FEIL,
                                ),
                                SimulertDetaljer(
                                    faktiskFraOgMed = 1.februar(2021),
                                    faktiskTilOgMed = 28.februar(2021),
                                    konto = konto,
                                    belop = -20779,
                                    tilbakeforing = true,
                                    sats = 0,
                                    typeSats = "",
                                    antallSats = 0,
                                    uforegrad = 0,
                                    klassekode = KlasseKode.SUUFORE,
                                    klassekodeBeskrivelse = suBeskrivelse,
                                    klasseType = KlasseType.YTEL,
                                ),
                            ),
                        ),
                    ),
                ),
                SimulertPeriode(
                    fraOgMed = 1.mars(2021),
                    tilOgMed = 31.mars(2021),
                    utbetaling = listOf(
                        SimulertUtbetaling(
                            fagSystemId = fagsystemId,
                            utbetalesTilId = fnr,
                            utbetalesTilNavn = navn,
                            forfall = 10.mars(2021),
                            feilkonto = false,
                            detaljer = listOf(
                                SimulertDetaljer(
                                    faktiskFraOgMed = 1.mars(2021),
                                    faktiskTilOgMed = 31.mars(2021),
                                    konto = konto,
                                    belop = 10000,
                                    tilbakeforing = false,
                                    sats = 10000,
                                    typeSats = typeSats,
                                    antallSats = 1,
                                    uforegrad = 0,
                                    klassekode = KlasseKode.SUUFORE,
                                    klassekodeBeskrivelse = suBeskrivelse,
                                    klasseType = KlasseType.YTEL,
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )
    }

    //language=xml
    private val xmlResponseMedFeilutbetaling = """
            <simulerBeregningResponse xmlns="http://nav.no/system/os/tjenester/simulerFpService/simulerFpServiceGrensesnitt">
         <response xmlns="">
            <simulering>
               <gjelderId>$fnr</gjelderId>
               <gjelderNavn>$navn</gjelderNavn>
               <datoBeregnet>2021-04-14</datoBeregnet>
               <kodeFaggruppe>INNT</kodeFaggruppe>
               <belop>5000.00</belop>
               <beregningsPeriode xmlns="http://nav.no/system/os/entiteter/beregningSkjema">
                  <periodeFom xmlns="">2021-02-01</periodeFom>
                  <periodeTom xmlns="">2021-02-28</periodeTom>
                  <beregningStoppnivaa>
                     <kodeFagomraade xmlns="">SUUFORE</kodeFagomraade>
                     <stoppNivaaId xmlns="">1</stoppNivaaId>
                     <behandlendeEnhet xmlns="">8020</behandlendeEnhet>
                     <oppdragsId xmlns="">53387554</oppdragsId>
                     <fagsystemId xmlns="">$fagsystemId</fagsystemId>
                     <kid xmlns=""/>
                     <utbetalesTilId xmlns="">$fnr</utbetalesTilId>
                     <utbetalesTilNavn xmlns="">$navn</utbetalesTilNavn>
                     <bilagsType xmlns="">U</bilagsType>
                     <forfall xmlns="">2021-04-14</forfall>
                     <feilkonto xmlns="">false</feilkonto>
                     <beregningStoppnivaaDetaljer>
                        <faktiskFom xmlns="">2021-02-01</faktiskFom>
                        <faktiskTom xmlns="">2021-02-28</faktiskTom>
                        <kontoStreng xmlns="">$konto</kontoStreng>
                        <behandlingskode xmlns="">2</behandlingskode>
                        <belop xmlns="">10779.00</belop>
                        <trekkVedtakId xmlns="">0</trekkVedtakId>
                        <stonadId xmlns=""></stonadId>
                        <korrigering xmlns=""></korrigering>
                        <tilbakeforing xmlns="">false</tilbakeforing>
                        <linjeId xmlns="">0</linjeId>
                        <sats xmlns="">0.00</sats>
                        <typeSats xmlns=""></typeSats>
                        <antallSats xmlns="">0.00</antallSats>
                        <saksbehId xmlns="">K231B215</saksbehId>
                        <uforeGrad xmlns="">0</uforeGrad>
                        <kravhaverId xmlns=""></kravhaverId>
                        <delytelseId xmlns=""></delytelseId>
                        <bostedsenhet xmlns="">8020</bostedsenhet>
                        <skykldnerId xmlns=""></skykldnerId>
                        <klassekode xmlns="">SUUFORE</klassekode>
                        <klasseKodeBeskrivelse xmlns="">Supplerende stønad Uføre</klasseKodeBeskrivelse>
                        <typeKlasse xmlns="">YTEL</typeKlasse>
                        <typeKlasseBeskrivelse xmlns="">Klassetype for ytelseskonti</typeKlasseBeskrivelse>
                        <refunderesOrgNr xmlns=""></refunderesOrgNr>
                     </beregningStoppnivaaDetaljer>
                     <beregningStoppnivaaDetaljer>
                        <faktiskFom xmlns="">2021-02-01</faktiskFom>
                        <faktiskTom xmlns="">2021-02-28</faktiskTom>
                        <kontoStreng xmlns="">$konto</kontoStreng>
                        <behandlingskode xmlns="">2</behandlingskode>
                        <belop xmlns="">10000.00</belop>
                        <trekkVedtakId xmlns="">0</trekkVedtakId>
                        <stonadId xmlns=""></stonadId>
                        <korrigering xmlns=""></korrigering>
                        <tilbakeforing xmlns="">false</tilbakeforing>
                        <linjeId xmlns="">3</linjeId>
                        <sats xmlns="">10000.00</sats>
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
                        <faktiskFom xmlns="">2021-02-01</faktiskFom>
                        <faktiskTom xmlns="">2021-02-28</faktiskTom>
                        <kontoStreng xmlns="">$konto</kontoStreng>
                        <behandlingskode xmlns="">0</behandlingskode>
                        <belop xmlns="">10779.00</belop>
                        <trekkVedtakId xmlns="">0</trekkVedtakId>
                        <stonadId xmlns=""></stonadId>
                        <korrigering xmlns="">J</korrigering>
                        <tilbakeforing xmlns="">false</tilbakeforing>
                        <linjeId xmlns="">0</linjeId>
                        <sats xmlns="">0.00</sats>
                        <typeSats xmlns=""></typeSats>
                        <antallSats xmlns="">0.00</antallSats>
                        <saksbehId xmlns="">K231B215</saksbehId>
                        <uforeGrad xmlns="">0</uforeGrad>
                        <kravhaverId xmlns=""></kravhaverId>
                        <delytelseId xmlns=""></delytelseId>
                        <bostedsenhet xmlns="">8020</bostedsenhet>
                        <skykldnerId xmlns=""></skykldnerId>
                        <klassekode xmlns="">KL_KODE_FEIL_INNT</klassekode>
                        <klasseKodeBeskrivelse xmlns="">Feilutbetaling Inntektsytelser</klasseKodeBeskrivelse>
                        <typeKlasse xmlns="">FEIL</typeKlasse>
                        <typeKlasseBeskrivelse xmlns="">Klassetype for feilkontoer</typeKlasseBeskrivelse>
                        <refunderesOrgNr xmlns=""></refunderesOrgNr>
                     </beregningStoppnivaaDetaljer>
                     <beregningStoppnivaaDetaljer>
                        <faktiskFom xmlns="">2021-02-01</faktiskFom>
                        <faktiskTom xmlns="">2021-02-28</faktiskTom>
                        <kontoStreng xmlns="">$konto</kontoStreng>
                        <behandlingskode xmlns="">0</behandlingskode>
                        <belop xmlns="">-10779.00</belop>
                        <trekkVedtakId xmlns="">0</trekkVedtakId>
                        <stonadId xmlns=""></stonadId>
                        <korrigering xmlns=""></korrigering>
                        <tilbakeforing xmlns="">false</tilbakeforing>
                        <linjeId xmlns="">0</linjeId>
                        <sats xmlns="">0.00</sats>
                        <typeSats xmlns=""></typeSats>
                        <antallSats xmlns="">0.00</antallSats>
                        <saksbehId xmlns="">K231B215</saksbehId>
                        <uforeGrad xmlns="">0</uforeGrad>
                        <kravhaverId xmlns=""></kravhaverId>
                        <delytelseId xmlns=""></delytelseId>
                        <bostedsenhet xmlns="">8020</bostedsenhet>
                        <skykldnerId xmlns=""></skykldnerId>
                        <klassekode xmlns="">TBMOTOBS</klassekode>
                        <klasseKodeBeskrivelse xmlns="">Feilutbetaling motkonto til OBS konto</klasseKodeBeskrivelse>
                        <typeKlasse xmlns="">MOTP</typeKlasse>
                        <typeKlasseBeskrivelse xmlns="">Klassetype for motposteringskonto</typeKlasseBeskrivelse>
                        <refunderesOrgNr xmlns=""></refunderesOrgNr>
                     </beregningStoppnivaaDetaljer>
                     <beregningStoppnivaaDetaljer>
                        <faktiskFom xmlns="">2021-02-01</faktiskFom>
                        <faktiskTom xmlns="">2021-02-28</faktiskTom>
                        <kontoStreng xmlns="">$konto</kontoStreng>
                        <behandlingskode xmlns="">2</behandlingskode>
                        <belop xmlns="">-20779.00</belop>
                        <trekkVedtakId xmlns="">0</trekkVedtakId>
                        <stonadId xmlns=""></stonadId>
                        <korrigering xmlns=""></korrigering>
                        <tilbakeforing xmlns="">true</tilbakeforing>
                        <linjeId xmlns="">1</linjeId>
                        <sats xmlns="">0.00</sats>
                        <typeSats xmlns=""></typeSats>
                        <antallSats xmlns="">0.00</antallSats>
                        <saksbehId xmlns="">K231B215</saksbehId>
                        <uforeGrad xmlns="">0</uforeGrad>
                        <kravhaverId xmlns=""></kravhaverId>
                        <delytelseId xmlns=""></delytelseId>
                        <bostedsenhet xmlns="">8020</bostedsenhet>
                        <skykldnerId xmlns=""></skykldnerId>
                        <klassekode xmlns="">SUUFORE</klassekode>
                        <klasseKodeBeskrivelse xmlns="">Supplerende stønad Uføre</klasseKodeBeskrivelse>
                        <typeKlasse xmlns="">YTEL</typeKlasse>
                        <typeKlasseBeskrivelse xmlns="">Klassetype for ytelseskonti</typeKlasseBeskrivelse>
                        <refunderesOrgNr xmlns=""></refunderesOrgNr>
                     </beregningStoppnivaaDetaljer>
                  </beregningStoppnivaa>
               </beregningsPeriode>
               <beregningsPeriode xmlns="http://nav.no/system/os/entiteter/beregningSkjema">
                  <periodeFom xmlns="">2021-03-01</periodeFom>
                  <periodeTom xmlns="">2021-03-31</periodeTom>
                  <beregningStoppnivaa>
                     <kodeFagomraade xmlns="">SUUFORE</kodeFagomraade>
                     <stoppNivaaId xmlns="">2</stoppNivaaId>
                     <behandlendeEnhet xmlns="">8020</behandlendeEnhet>
                     <oppdragsId xmlns="">53387554</oppdragsId>
                     <fagsystemId xmlns="">$fagsystemId</fagsystemId>
                     <kid xmlns=""/>
                     <utbetalesTilId xmlns="">$fnr</utbetalesTilId>
                     <utbetalesTilNavn xmlns="">$navn</utbetalesTilNavn>
                     <bilagsType xmlns="">U</bilagsType>
                     <forfall xmlns="">2021-03-10</forfall>
                     <feilkonto xmlns="">false</feilkonto>
                     <beregningStoppnivaaDetaljer>
                        <faktiskFom xmlns="">2021-03-01</faktiskFom>
                        <faktiskTom xmlns="">2021-03-31</faktiskTom>
                        <kontoStreng xmlns="">$konto</kontoStreng>
                        <behandlingskode xmlns="">2</behandlingskode>
                        <belop xmlns="">10000.00</belop>
                        <trekkVedtakId xmlns="">0</trekkVedtakId>
                        <stonadId xmlns=""></stonadId>
                        <korrigering xmlns=""></korrigering>
                        <tilbakeforing xmlns="">false</tilbakeforing>
                        <linjeId xmlns="">3</linjeId>
                        <sats xmlns="">10000.00</sats>
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
                        <faktiskFom xmlns="">2021-03-01</faktiskFom>
                        <faktiskTom xmlns="">2021-03-31</faktiskTom>
                        <kontoStreng xmlns="">0510000</kontoStreng>
                        <behandlingskode xmlns="">0</behandlingskode>
                        <belop xmlns="">-5000.00</belop>
                        <trekkVedtakId xmlns="">11845513</trekkVedtakId>
                        <stonadId xmlns=""></stonadId>
                        <korrigering xmlns=""></korrigering>
                        <tilbakeforing xmlns="">false</tilbakeforing>
                        <linjeId xmlns="">0</linjeId>
                        <sats xmlns="">0.00</sats>
                        <typeSats xmlns="">MND</typeSats>
                        <antallSats xmlns="">31.00</antallSats>
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

    @Test
    fun `mapper simulerte etterbetalinger`() {
        val responseMedEtterbetaling =
            XmlMapper.readValue(xmlResponseMedEtterbetaling, GrensesnittResponse::class.java).response
        SimuleringResponseMapper(responseMedEtterbetaling, fixedClock).simulering shouldBe Simulering(
            gjelderId = fnr,
            gjelderNavn = navn,
            datoBeregnet = 14.april(2021),
            nettoBeløp = 19611,
            periodeList = listOf(
                SimulertPeriode(
                    fraOgMed = 1.februar(2021),
                    tilOgMed = 28.februar(2021),
                    utbetaling = listOf(
                        SimulertUtbetaling(
                            fagSystemId = fagsystemId,
                            utbetalesTilId = fnr,
                            utbetalesTilNavn = navn,
                            forfall = 14.april(2021),
                            feilkonto = false,
                            detaljer = listOf(
                                SimulertDetaljer(
                                    faktiskFraOgMed = 1.februar(2021),
                                    faktiskTilOgMed = 28.februar(2021),
                                    konto = konto,
                                    belop = 30000,
                                    tilbakeforing = false,
                                    sats = 30000,
                                    typeSats = typeSats,
                                    antallSats = 1,
                                    uforegrad = 0,
                                    klassekode = KlasseKode.SUUFORE,
                                    klassekodeBeskrivelse = suBeskrivelse,
                                    klasseType = KlasseType.YTEL,
                                ),
                                SimulertDetaljer(
                                    faktiskFraOgMed = 1.februar(2021),
                                    faktiskTilOgMed = 28.februar(2021),
                                    konto = konto,
                                    belop = -20779,
                                    tilbakeforing = true,
                                    sats = 0,
                                    typeSats = "",
                                    antallSats = 0,
                                    uforegrad = 0,
                                    klassekode = KlasseKode.SUUFORE,
                                    klassekodeBeskrivelse = suBeskrivelse,
                                    klasseType = KlasseType.YTEL,
                                ),
                            ),
                        ),
                    ),
                ),
                SimulertPeriode(
                    fraOgMed = 1.mars(2021),
                    tilOgMed = 31.mars(2021),
                    utbetaling = listOf(
                        SimulertUtbetaling(
                            fagSystemId = fagsystemId,
                            utbetalesTilId = fnr,
                            utbetalesTilNavn = navn,
                            forfall = 10.mars(2021),
                            feilkonto = false,
                            detaljer = listOf(
                                SimulertDetaljer(
                                    faktiskFraOgMed = 1.mars(2021),
                                    faktiskTilOgMed = 31.mars(2021),
                                    konto = konto,
                                    belop = 30000,
                                    tilbakeforing = false,
                                    sats = 30000,
                                    typeSats = typeSats,
                                    antallSats = 1,
                                    uforegrad = 0,
                                    klassekode = KlasseKode.SUUFORE,
                                    klassekodeBeskrivelse = suBeskrivelse,
                                    klasseType = KlasseType.YTEL,
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )
    }

    //language=xml
    private val xmlResponseMedEtterbetaling = """
     <simulerBeregningResponse xmlns="http://nav.no/system/os/tjenester/simulerFpService/simulerFpServiceGrensesnitt">
         <response xmlns="">
            <simulering>
               <gjelderId>$fnr</gjelderId>
               <gjelderNavn>$navn</gjelderNavn>
               <datoBeregnet>2021-04-14</datoBeregnet>
               <kodeFaggruppe>INNT</kodeFaggruppe>
               <belop>19611.00</belop>
               <beregningsPeriode xmlns="http://nav.no/system/os/entiteter/beregningSkjema">
                  <periodeFom xmlns="">2021-02-01</periodeFom>
                  <periodeTom xmlns="">2021-02-28</periodeTom>
                  <beregningStoppnivaa>
                     <kodeFagomraade xmlns="">SUUFORE</kodeFagomraade>
                     <stoppNivaaId xmlns="">1</stoppNivaaId>
                     <behandlendeEnhet xmlns="">8020</behandlendeEnhet>
                     <oppdragsId xmlns="">53387554</oppdragsId>
                     <fagsystemId xmlns="">$fagsystemId</fagsystemId>
                     <kid xmlns=""/>
                     <utbetalesTilId xmlns="">$fnr</utbetalesTilId>
                     <utbetalesTilNavn xmlns="">$navn</utbetalesTilNavn>
                     <bilagsType xmlns="">U</bilagsType>
                     <forfall xmlns="">2021-04-14</forfall>
                     <feilkonto xmlns="">false</feilkonto>
                     <beregningStoppnivaaDetaljer>
                        <faktiskFom xmlns="">2021-02-01</faktiskFom>
                        <faktiskTom xmlns="">2021-02-28</faktiskTom>
                        <kontoStreng xmlns="">$konto</kontoStreng>
                        <behandlingskode xmlns="">2</behandlingskode>
                        <belop xmlns="">30000.00</belop>
                        <trekkVedtakId xmlns="">0</trekkVedtakId>
                        <stonadId xmlns=""></stonadId>
                        <korrigering xmlns=""></korrigering>
                        <tilbakeforing xmlns="">false</tilbakeforing>
                        <linjeId xmlns="">3</linjeId>
                        <sats xmlns="">30000.00</sats>
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
                        <faktiskFom xmlns="">2021-02-01</faktiskFom>
                        <faktiskTom xmlns="">2021-02-28</faktiskTom>
                        <kontoStreng xmlns="">0510000</kontoStreng>
                        <behandlingskode xmlns="">0</behandlingskode>
                        <belop xmlns="">-4610.00</belop>
                        <trekkVedtakId xmlns="">11845513</trekkVedtakId>
                        <stonadId xmlns=""></stonadId>
                        <korrigering xmlns=""></korrigering>
                        <tilbakeforing xmlns="">false</tilbakeforing>
                        <linjeId xmlns="">0</linjeId>
                        <sats xmlns="">0.00</sats>
                        <typeSats xmlns="">MND</typeSats>
                        <antallSats xmlns="">28.00</antallSats>
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
                     <beregningStoppnivaaDetaljer>
                        <faktiskFom xmlns="">2021-02-01</faktiskFom>
                        <faktiskTom xmlns="">2021-02-28</faktiskTom>
                        <kontoStreng xmlns="">$konto</kontoStreng>
                        <behandlingskode xmlns="">2</behandlingskode>
                        <belop xmlns="">-20779.00</belop>
                        <trekkVedtakId xmlns="">0</trekkVedtakId>
                        <stonadId xmlns=""></stonadId>
                        <korrigering xmlns=""></korrigering>
                        <tilbakeforing xmlns="">true</tilbakeforing>
                        <linjeId xmlns="">1</linjeId>
                        <sats xmlns="">0.00</sats>
                        <typeSats xmlns=""></typeSats>
                        <antallSats xmlns="">0.00</antallSats>
                        <saksbehId xmlns="">K231B215</saksbehId>
                        <uforeGrad xmlns="">0</uforeGrad>
                        <kravhaverId xmlns=""></kravhaverId>
                        <delytelseId xmlns=""></delytelseId>
                        <bostedsenhet xmlns="">8020</bostedsenhet>
                        <skykldnerId xmlns=""></skykldnerId>
                        <klassekode xmlns="">SUUFORE</klassekode>
                        <klasseKodeBeskrivelse xmlns="">Supplerende stønad Uføre</klasseKodeBeskrivelse>
                        <typeKlasse xmlns="">YTEL</typeKlasse>
                        <typeKlasseBeskrivelse xmlns="">Klassetype for ytelseskonti</typeKlasseBeskrivelse>
                        <refunderesOrgNr xmlns=""></refunderesOrgNr>
                     </beregningStoppnivaaDetaljer>
                  </beregningStoppnivaa>
               </beregningsPeriode>
               <beregningsPeriode xmlns="http://nav.no/system/os/entiteter/beregningSkjema">
                  <periodeFom xmlns="">2021-03-01</periodeFom>
                  <periodeTom xmlns="">2021-03-31</periodeTom>
                  <beregningStoppnivaa>
                     <kodeFagomraade xmlns="">SUUFORE</kodeFagomraade>
                     <stoppNivaaId xmlns="">2</stoppNivaaId>
                     <behandlendeEnhet xmlns="">8020</behandlendeEnhet>
                     <oppdragsId xmlns="">53387554</oppdragsId>
                     <fagsystemId xmlns="">$fagsystemId</fagsystemId>
                     <kid xmlns=""/>
                     <utbetalesTilId xmlns="">$fnr</utbetalesTilId>
                     <utbetalesTilNavn xmlns="">$navn</utbetalesTilNavn>
                     <bilagsType xmlns="">U</bilagsType>
                     <forfall xmlns="">2021-03-10</forfall>
                     <feilkonto xmlns="">false</feilkonto>
                     <beregningStoppnivaaDetaljer>
                        <faktiskFom xmlns="">2021-03-01</faktiskFom>
                        <faktiskTom xmlns="">2021-03-31</faktiskTom>
                        <kontoStreng xmlns="">$konto</kontoStreng>
                        <behandlingskode xmlns="">2</behandlingskode>
                        <belop xmlns="">30000.00</belop>
                        <trekkVedtakId xmlns="">0</trekkVedtakId>
                        <stonadId xmlns=""></stonadId>
                        <korrigering xmlns=""></korrigering>
                        <tilbakeforing xmlns="">false</tilbakeforing>
                        <linjeId xmlns="">3</linjeId>
                        <sats xmlns="">30000.00</sats>
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
                        <faktiskFom xmlns="">2021-03-01</faktiskFom>
                        <faktiskTom xmlns="">2021-03-31</faktiskTom>
                        <kontoStreng xmlns="">0510000</kontoStreng>
                        <behandlingskode xmlns="">0</behandlingskode>
                        <belop xmlns="">-15000.00</belop>
                        <trekkVedtakId xmlns="">11845513</trekkVedtakId>
                        <stonadId xmlns=""></stonadId>
                        <korrigering xmlns=""></korrigering>
                        <tilbakeforing xmlns="">false</tilbakeforing>
                        <linjeId xmlns="">0</linjeId>
                        <sats xmlns="">0.00</sats>
                        <typeSats xmlns="">MND</typeSats>
                        <antallSats xmlns="">31.00</antallSats>
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

    @Test
    fun `filtrerer vekk detaljer som er ukjent eller uinteressant`() {
        val responseMedFremtidigUtbetaling =
            XmlMapper.readValue(xmlResponseMedUinteressanteKoder, GrensesnittResponse::class.java).response
        SimuleringResponseMapper(responseMedFremtidigUtbetaling, fixedClock).simulering shouldBe Simulering(
            gjelderId = fnr,
            gjelderNavn = navn,
            datoBeregnet = 14.april(2021),
            nettoBeløp = 10390,
            periodeList = listOf(
                SimulertPeriode(
                    fraOgMed = 1.april(2021),
                    tilOgMed = 30.april(2021),
                    utbetaling = listOf(
                        SimulertUtbetaling(
                            fagSystemId = fagsystemId,
                            utbetalesTilId = fnr,
                            utbetalesTilNavn = navn,
                            forfall = 19.april(2021),
                            feilkonto = false,
                            detaljer = listOf(
                                SimulertDetaljer(
                                    faktiskFraOgMed = 1.april(2021),
                                    faktiskTilOgMed = 30.april(2021),
                                    konto = konto,
                                    belop = 20779,
                                    tilbakeforing = false,
                                    sats = 20779,
                                    typeSats = typeSats,
                                    antallSats = 1,
                                    uforegrad = 0,
                                    klassekode = KlasseKode.SUUFORE,
                                    klassekodeBeskrivelse = suBeskrivelse,
                                    klasseType = KlasseType.YTEL,
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )
    }

    //language=xml
    private val xmlResponseMedUinteressanteKoder = """
    <simulerBeregningResponse xmlns="http://nav.no/system/os/tjenester/simulerFpService/simulerFpServiceGrensesnitt">
             <response xmlns="">
                <simulering>
                   <gjelderId>$fnr</gjelderId>
                   <gjelderNavn>$navn</gjelderNavn>
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
                         <fagsystemId xmlns="">$fagsystemId</fagsystemId>
                         <kid xmlns=""/>
                         <utbetalesTilId xmlns="">$fnr</utbetalesTilId>
                         <utbetalesTilNavn xmlns="">$navn</utbetalesTilNavn>
                         <bilagsType xmlns="">U</bilagsType>
                         <forfall xmlns="">2021-04-19</forfall>
                         <feilkonto xmlns="">false</feilkonto>
                         <beregningStoppnivaaDetaljer>
                            <faktiskFom xmlns="">2021-04-01</faktiskFom>
                            <faktiskTom xmlns="">2021-04-30</faktiskTom>
                            <kontoStreng xmlns="">$konto</kontoStreng>
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
                            <klassekode xmlns="">TULL</klassekode>
                            <klasseKodeBeskrivelse xmlns="">Tull</klasseKodeBeskrivelse>
                            <typeKlasse xmlns="">TØYS</typeKlasse>
                            <typeKlasseBeskrivelse xmlns="">Tøys</typeKlasseBeskrivelse>
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
