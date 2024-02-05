package no.nav.su.se.bakover.client.oppdrag.simulering

import arrow.core.nonEmptyListOf
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.client.oppdrag.utbetaling.UtbetalingRequest
import no.nav.su.se.bakover.client.oppdrag.utbetaling.UtbetalingRequestTest
import no.nav.su.se.bakover.client.oppdrag.utbetaling.toUtbetalingRequest
import no.nav.su.se.bakover.common.Rekkefølge
import no.nav.su.se.bakover.common.extensions.desember
import no.nav.su.se.bakover.common.extensions.februar
import no.nav.su.se.bakover.common.extensions.mai
import no.nav.su.se.bakover.common.extensions.oktober
import no.nav.su.se.bakover.common.extensions.september
import no.nav.su.se.bakover.common.extensions.startOfDay
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.common.tid.periode.år
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.xml.shouldBeSimilarXmlTo
import no.nav.system.os.tjenester.simulerfpservice.simulerfpserviceservicetypes.Oppdrag
import no.nav.system.os.tjenester.simulerfpservice.simulerfpserviceservicetypes.Oppdragslinje
import no.nav.system.os.tjenester.simulerfpservice.simulerfpserviceservicetypes.SimulerBeregningRequest
import org.junit.jupiter.api.Test
import økonomi.domain.avstemming.Avstemmingsnøkkel
import økonomi.domain.utbetaling.Utbetalingslinje
import java.math.BigDecimal
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

    @Suppress("unused")
    private fun SimulerBeregningRequest.SimuleringsPeriode.assert(fraOgMed: String, tilOgMed: String) {
        this.datoSimulerFom shouldBe fraOgMed
        this.datoSimulerTom shouldBe tilOgMed
    }

    @Suppress("unused")
    private fun Oppdragslinje.assert(oppdragslinje: UtbetalingRequest.Oppdragslinje) {
        delytelseId shouldBe oppdragslinje.delytelseId
        kodeEndringLinje shouldBe oppdragslinje.kodeEndringLinje.value
        sats shouldBe BigDecimal(oppdragslinje.sats)
        typeSats shouldBe oppdragslinje.typeSats.value
        datoVedtakFom shouldBe oppdragslinje.datoVedtakFom
        datoVedtakTom shouldBe oppdragslinje.datoVedtakTom
        utbetalesTilId shouldBe oppdragslinje.utbetalesTilId
        refDelytelseId shouldBe oppdragslinje.refDelytelseId
        refFagsystemId shouldBe oppdragslinje.refFagsystemId
        kodeKlassifik shouldBe oppdragslinje.kodeKlassifik
        fradragTillegg.value() shouldBe oppdragslinje.fradragTillegg.value
        saksbehId shouldBe oppdragslinje.saksbehId
        brukKjoreplan shouldBe oppdragslinje.brukKjoreplan.value
        attestant[0].attestantId shouldBe oppdragslinje.saksbehId
        kodeStatusLinje?.value() shouldBe oppdragslinje.kodeStatusLinje?.value
        datoStatusFom shouldBe oppdragslinje.datoStatusFom
    }

    @Suppress("unused")
    private fun Oppdrag.assert(utbetalingsRequest: UtbetalingRequest.OppdragRequest) {
        oppdragGjelderId shouldBe utbetalingsRequest.oppdragGjelderId
        saksbehId shouldBe utbetalingsRequest.saksbehId
        fagsystemId shouldBe utbetalingsRequest.fagsystemId
        kodeEndring shouldBe utbetalingsRequest.kodeEndring.value
        kodeFagomraade shouldBe utbetalingsRequest.kodeFagomraade
        utbetFrekvens shouldBe utbetalingsRequest.utbetFrekvens.value
        datoOppdragGjelderFom shouldBe utbetalingsRequest.datoOppdragGjelderFom
        enhet[0].datoEnhetFom shouldBe utbetalingsRequest.oppdragsEnheter[0].datoEnhetFom
        enhet[0].enhet shouldBe utbetalingsRequest.oppdragsEnheter[0].enhet
        enhet[0].typeEnhet shouldBe utbetalingsRequest.oppdragsEnheter[0].typeEnhet
    }
}
