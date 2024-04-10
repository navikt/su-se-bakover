package no.nav.su.se.bakover.client.oppdrag.simulering

import arrow.core.nonEmptyListOf
import no.nav.su.se.bakover.client.oppdrag.utbetaling.UtbetalingRequestTest
import no.nav.su.se.bakover.client.oppdrag.utbetaling.toUtbetalingRequest
import no.nav.su.se.bakover.common.Rekkefølge
import no.nav.su.se.bakover.common.domain.tid.desember
import no.nav.su.se.bakover.common.domain.tid.februar
import no.nav.su.se.bakover.common.domain.tid.mai
import no.nav.su.se.bakover.common.domain.tid.oktober
import no.nav.su.se.bakover.common.domain.tid.september
import no.nav.su.se.bakover.common.domain.tid.startOfDay
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.common.tid.periode.år
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.xml.shouldBeSimilarXmlTo
import org.junit.jupiter.api.Test
import økonomi.domain.avstemming.Avstemmingsnøkkel
import økonomi.domain.utbetaling.Utbetalingslinje
import java.time.Clock

internal class SimuleringRequestBuilderTest {

    @Test
    fun `bygger simulering request til bruker uten eksisterende oppdragslinjer`() {
        val utbetalingsRequest = UtbetalingRequestTest.utbetalingRequestFørstegangsutbetaling.oppdragRequest
        val actual = buildXmlRequestBody(
            simuleringsperiode = år(2020),
            request = utbetalingsRequest,
        )
        val expected = """
<ns2:simulerBeregningRequest
	xmlns:ns2="http://nav.no/system/os/tjenester/simulerFpService/simulerFpServiceGrensesnitt"
	xmlns:ns3="http://nav.no/system/os/entiteter/oppdragSkjema">
	<request>
		<oppdrag>
			<kodeEndring>NY</kodeEndring>
			<kodeFagomraade>SUUFORE</kodeFagomraade>
			<fagsystemId>2021</fagsystemId>
			<utbetFrekvens>MND</utbetFrekvens>
			<oppdragGjelderId>12345678911</oppdragGjelderId>
			<datoOppdragGjelderFom>1970-01-01</datoOppdragGjelderFom>
			<saksbehId>SU</saksbehId>
			<ns3:enhet>
				<typeEnhet>BOS</typeEnhet>
				<enhet>8020</enhet>
				<datoEnhetFom>1970-01-01</datoEnhetFom>
			</ns3:enhet>
			<oppdragslinje>
				<kodeEndringLinje>NY</kodeEndringLinje>
			    <delytelseId>${utbetalingsRequest.oppdragslinjer[0].delytelseId}</delytelseId>
				<kodeKlassifik>SUUFORE</kodeKlassifik>
				<datoVedtakFom>2020-01-01</datoVedtakFom>
				<datoVedtakTom>2020-04-30</datoVedtakTom>
				<sats>1000</sats>
				<fradragTillegg>T</fradragTillegg>
				<typeSats>MND</typeSats>
				<brukKjoreplan>N</brukKjoreplan>
				<saksbehId>SU</saksbehId>
				<utbetalesTilId>12345678911</utbetalesTilId>
			    <henvisning>${utbetalingsRequest.utbetalingsId()}</henvisning>
				<ns3:grad>
					<typeGrad>UFOR</typeGrad>
					<grad>50</grad>
				</ns3:grad>
				<ns3:attestant>
					<attestantId>SU</attestantId>
				</ns3:attestant>
			</oppdragslinje>
			<oppdragslinje>
				<kodeEndringLinje>NY</kodeEndringLinje>
				<delytelseId>${utbetalingsRequest.oppdragslinjer[1].delytelseId}</delytelseId>
				<kodeKlassifik>SUUFORE</kodeKlassifik>
				<datoVedtakFom>2020-05-01</datoVedtakFom>
				<datoVedtakTom>2020-12-31</datoVedtakTom>
				<sats>1000</sats>
				<fradragTillegg>T</fradragTillegg>
				<typeSats>MND</typeSats>
				<brukKjoreplan>N</brukKjoreplan>
				<saksbehId>SU</saksbehId>
				<utbetalesTilId>12345678911</utbetalesTilId>
			    <henvisning>${utbetalingsRequest.utbetalingsId()}</henvisning>
				<refFagsystemId>2021</refFagsystemId>
				<refDelytelseId>${utbetalingsRequest.oppdragslinjer[0].delytelseId}</refDelytelseId>
				<ns3:grad>
					<typeGrad>UFOR</typeGrad>
					<grad>70</grad>
				</ns3:grad>
				<ns3:attestant>
					<attestantId>SU</attestantId>
				</ns3:attestant>
			</oppdragslinje>
		</oppdrag>
		<simuleringsPeriode>
			<datoSimulerFom>2020-01-01</datoSimulerFom>
			<datoSimulerTom>2020-12-31</datoSimulerTom>
		</simuleringsPeriode>
	</request>
</ns2:simulerBeregningRequest>
        """.trimIndent()
        actual.shouldBeSimilarXmlTo(expected, true)
    }

    @Test
    fun `bygger simulering request ved endring av eksisterende oppdragslinjer`() {
        val linjeSomSkalEndres = UtbetalingRequestTest.nyUtbetaling.sisteUtbetalingslinje()

        val linjeMedEndring = Utbetalingslinje.Endring.Opphør(
            utbetalingslinjeSomSkalEndres = linjeSomSkalEndres,
            virkningsperiode = Periode.create(1.februar(2020), linjeSomSkalEndres.periode.tilOgMed),
            clock = fixedClock,
            rekkefølge = Rekkefølge.start(),
        )
        val utbetalingMedEndring = UtbetalingRequestTest.nyUtbetaling.copy(
            avstemmingsnøkkel = Avstemmingsnøkkel(18.september(2020).startOfDay()),
            utbetalingslinjer = nonEmptyListOf(linjeMedEndring),
        )

        val utbetalingsRequest = toUtbetalingRequest(utbetalingMedEndring).oppdragRequest
        val acutal = buildXmlRequestBody(
            simuleringsperiode = Periode.create(
                fraOgMed = 1.februar(2020),
                tilOgMed = 31.desember(2020),
            ),
            request = utbetalingsRequest,
        )
        val expected = """
<ns2:simulerBeregningRequest xmlns:ns2="http://nav.no/system/os/tjenester/simulerFpService/simulerFpServiceGrensesnitt"
                             xmlns:ns3="http://nav.no/system/os/entiteter/oppdragSkjema">
    <request>
        <oppdrag>
            <kodeEndring>ENDR</kodeEndring>
            <kodeFagomraade>SUUFORE</kodeFagomraade>
            <fagsystemId>2021</fagsystemId>
            <utbetFrekvens>MND</utbetFrekvens>
            <oppdragGjelderId>12345678911</oppdragGjelderId>
            <datoOppdragGjelderFom>1970-01-01</datoOppdragGjelderFom>
            <saksbehId>SU</saksbehId>
            <ns3:enhet>
                <typeEnhet>BOS</typeEnhet>
                <enhet>8020</enhet>
                <datoEnhetFom>1970-01-01</datoEnhetFom>
            </ns3:enhet>
            <oppdragslinje>
                <kodeEndringLinje>ENDR</kodeEndringLinje>
                <kodeStatusLinje>OPPH</kodeStatusLinje>
                <datoStatusFom>2020-02-01</datoStatusFom>
                <delytelseId>${utbetalingsRequest.oppdragslinjer[0].delytelseId}</delytelseId>
                <kodeKlassifik>SUUFORE</kodeKlassifik>
                <datoVedtakFom>2020-05-01</datoVedtakFom>
                <datoVedtakTom>2020-12-31</datoVedtakTom>
                <sats>1000</sats>
                <fradragTillegg>T</fradragTillegg>
                <typeSats>MND</typeSats>
                <brukKjoreplan>N</brukKjoreplan>
                <saksbehId>SU</saksbehId>
                <utbetalesTilId>12345678911</utbetalesTilId>
			    <henvisning>${utbetalingsRequest.utbetalingsId()}</henvisning>
                <ns3:grad>
                    <typeGrad>UFOR</typeGrad>
                    <grad>70</grad>
                </ns3:grad>
                <ns3:attestant>
                    <attestantId>SU</attestantId>
                </ns3:attestant>
            </oppdragslinje>
        </oppdrag>
        <simuleringsPeriode>
            <datoSimulerFom>2020-02-01</datoSimulerFom>
            <datoSimulerTom>2020-12-31</datoSimulerTom>
        </simuleringsPeriode>
    </request>
</ns2:simulerBeregningRequest>
        """
        acutal.shouldBeSimilarXmlTo(expected, true)
    }

    @Test
    fun `opphører fra oktober og ut men simulerer hele siste utbetalingslinje`() {
        val linjeSomSkalEndres = UtbetalingRequestTest.nyUtbetaling.sisteUtbetalingslinje()

        val linjeMedEndring = Utbetalingslinje.Endring.Opphør(
            utbetalingslinjeSomSkalEndres = linjeSomSkalEndres,
            virkningsperiode = Periode.create(1.oktober(2020), linjeSomSkalEndres.periode.tilOgMed),
            clock = Clock.systemUTC(),
            rekkefølge = Rekkefølge.start(),
        )
        val utbetalingMedEndring = UtbetalingRequestTest.nyUtbetaling.copy(
            avstemmingsnøkkel = Avstemmingsnøkkel(18.september(2020).startOfDay()),
            utbetalingslinjer = nonEmptyListOf(linjeMedEndring),
        )

        val utbetalingsRequest = toUtbetalingRequest(utbetalingMedEndring).oppdragRequest
        val actual = buildXmlRequestBody(
            simuleringsperiode = Periode.create(
                fraOgMed = 1.mai(2020),
                tilOgMed = 31.desember(2020),
            ),
            request = utbetalingsRequest,
        )
        val expected = """
<ns2:simulerBeregningRequest xmlns:ns2="http://nav.no/system/os/tjenester/simulerFpService/simulerFpServiceGrensesnitt"
                             xmlns:ns3="http://nav.no/system/os/entiteter/oppdragSkjema">
    <request>
        <oppdrag>
            <kodeEndring>ENDR</kodeEndring>
            <kodeFagomraade>SUUFORE</kodeFagomraade>
            <fagsystemId>2021</fagsystemId>
            <utbetFrekvens>MND</utbetFrekvens>
            <oppdragGjelderId>12345678911</oppdragGjelderId>
            <datoOppdragGjelderFom>1970-01-01</datoOppdragGjelderFom>
            <saksbehId>SU</saksbehId>
            <ns3:enhet>
                <typeEnhet>BOS</typeEnhet>
                <enhet>8020</enhet>
                <datoEnhetFom>1970-01-01</datoEnhetFom>
            </ns3:enhet>
            <oppdragslinje>
                <kodeEndringLinje>ENDR</kodeEndringLinje>
                <kodeStatusLinje>OPPH</kodeStatusLinje>
                <datoStatusFom>2020-10-01</datoStatusFom>
                <delytelseId>${utbetalingsRequest.oppdragslinjer[0].delytelseId}</delytelseId>
                <kodeKlassifik>SUUFORE</kodeKlassifik>
                <datoVedtakFom>2020-05-01</datoVedtakFom>
                <datoVedtakTom>2020-12-31</datoVedtakTom>
                <sats>1000</sats>
                <fradragTillegg>T</fradragTillegg>
                <typeSats>MND</typeSats>
                <brukKjoreplan>N</brukKjoreplan>
                <saksbehId>SU</saksbehId>
                <utbetalesTilId>12345678911</utbetalesTilId>
			    <henvisning>${utbetalingsRequest.utbetalingsId()}</henvisning>
                <ns3:grad>
                    <typeGrad>UFOR</typeGrad>
                    <grad>70</grad>
                </ns3:grad>
                <ns3:attestant>
                    <attestantId>SU</attestantId>
                </ns3:attestant>
            </oppdragslinje>
        </oppdrag>
        <simuleringsPeriode>
            <datoSimulerFom>2020-05-01</datoSimulerFom>
            <datoSimulerTom>2020-12-31</datoSimulerTom>
        </simuleringsPeriode>
    </request>
</ns2:simulerBeregningRequest>
        """
        actual.shouldBeSimilarXmlTo(expected, true)
    }
}
