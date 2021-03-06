package no.nav.su.se.bakover.client.oppdrag.utbetaling

import no.nav.su.se.bakover.client.oppdrag.XmlMapper
import no.nav.su.se.bakover.client.oppdrag.avstemming.sakId
import no.nav.su.se.bakover.client.oppdrag.avstemming.saksnummer
import no.nav.su.se.bakover.common.februar
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.startOfDay
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingslinje
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemmingsnøkkel
import org.hamcrest.MatcherAssert.assertThat
import org.junit.jupiter.api.Test
import org.xmlunit.diff.DefaultNodeMatcher
import org.xmlunit.diff.ElementSelectors
import org.xmlunit.matchers.CompareMatcher.isSimilarTo

class UtbetalingXmlMappingTest {

    private val nodeMatcher = DefaultNodeMatcher().apply { ElementSelectors.byName }

    @Test
    fun `mapper utbetaling til xml request`() {
        assertThat(
            XmlMapper.writeValueAsString(toUtbetalingRequest(utbetaling = utbetaling)),
            isSimilarTo(expected).withNodeMatcher(nodeMatcher),
        )
    }

    private val førsteUtbetalingsLinje = Utbetalingslinje.Ny(
        fraOgMed = 1.januar(2020),
        tilOgMed = 31.januar(2020),
        beløp = 10,
        forrigeUtbetalingslinjeId = null,
    )
    private val andreUtbetalingslinje = Utbetalingslinje.Ny(
        fraOgMed = 1.februar(2020),
        tilOgMed = 29.februar(2020),
        beløp = 20,
        forrigeUtbetalingslinjeId = førsteUtbetalingsLinje.id,
    )

    private val tredjeUtbetalingslinje = Utbetalingslinje.Endring(
        andreUtbetalingslinje,
        statusendring = Utbetalingslinje.Statusendring(
            status = Utbetalingslinje.LinjeStatus.OPPHØR,
            fraOgMed = 1.februar(2020),
        ),
    )

    private val fnr = Fnr("12345678910")
    private val utbetaling = Utbetaling.UtbetalingForSimulering(
        sakId = sakId,
        saksnummer = saksnummer,
        utbetalingslinjer = listOf(
            førsteUtbetalingsLinje,
            andreUtbetalingslinje,
            tredjeUtbetalingslinje,
        ),
        fnr = fnr,
        type = Utbetaling.UtbetalingsType.NY,
        behandler = NavIdentBruker.Attestant("A123456"),
        avstemmingsnøkkel = Avstemmingsnøkkel(1.januar(2020).startOfDay()),
    )

    //language=xml
    private val expected =
        """
            <?xml version='1.0' encoding='UTF-8'?>
            <Oppdrag>
              <oppdrag-110>
                <kodeAksjon>1</kodeAksjon>
                <kodeEndring>NY</kodeEndring>
                <kodeFagomraade>SUUFORE</kodeFagomraade>
                <fagsystemId>$saksnummer</fagsystemId>
                <utbetFrekvens>MND</utbetFrekvens>
                <oppdragGjelderId>$fnr</oppdragGjelderId>
                <datoOppdragGjelderFom>1970-01-01</datoOppdragGjelderFom>
                <saksbehId>SU</saksbehId>
                <avstemming-115>
                  <kodeKomponent>SU</kodeKomponent>
                  <nokkelAvstemming>1577833200000000000</nokkelAvstemming>
                  <tidspktMelding>2020-01-01-00.00.00.000000</tidspktMelding>
                </avstemming-115>
                <oppdrags-enhet-120>
                  <typeEnhet>BOS</typeEnhet>
                  <enhet>8020</enhet>
                  <datoEnhetFom>1970-01-01</datoEnhetFom>
                </oppdrags-enhet-120>
                <oppdrags-linje-150>
                  <kodeEndringLinje>NY</kodeEndringLinje>
                  <delytelseId>${førsteUtbetalingsLinje.id}</delytelseId>
                  <kodeKlassifik>SUUFORE</kodeKlassifik>
                  <datoVedtakFom>2020-01-01</datoVedtakFom>
                  <datoVedtakTom>2020-01-31</datoVedtakTom>
                  <sats>10</sats>
                  <fradragTillegg>T</fradragTillegg>
                  <typeSats>MND</typeSats>
                  <brukKjoreplan>N</brukKjoreplan>
                  <saksbehId>SU</saksbehId>
                  <utbetalesTilId>$fnr</utbetalesTilId>
                  <attestant-180>
                    <attestantId>A123456</attestantId>
                  </attestant-180>
                </oppdrags-linje-150>
                <oppdrags-linje-150>
                  <kodeEndringLinje>NY</kodeEndringLinje>
                  <delytelseId>${andreUtbetalingslinje.id}</delytelseId>
                  <kodeKlassifik>SUUFORE</kodeKlassifik>
                  <datoVedtakFom>2020-02-01</datoVedtakFom>
                  <datoVedtakTom>2020-02-29</datoVedtakTom>
                  <sats>20</sats>
                  <fradragTillegg>T</fradragTillegg>
                  <typeSats>MND</typeSats>
                  <brukKjoreplan>N</brukKjoreplan>
                  <saksbehId>SU</saksbehId>
                  <utbetalesTilId>$fnr</utbetalesTilId>
                  <refDelytelseId>${førsteUtbetalingsLinje.id}</refDelytelseId>
                  <refFagsystemId>$saksnummer</refFagsystemId>
                  <attestant-180>
                    <attestantId>A123456</attestantId>
                  </attestant-180>
                </oppdrags-linje-150>
                <oppdrags-linje-150>
                  <kodeEndringLinje>ENDR</kodeEndringLinje>
                  <kodeStatusLinje>OPPH</kodeStatusLinje>
                  <datoStatusFom>2020-02-01</datoStatusFom>
                  <delytelseId>${tredjeUtbetalingslinje.id}</delytelseId>
                  <kodeKlassifik>SUUFORE</kodeKlassifik>
                  <datoVedtakFom>2020-02-01</datoVedtakFom>
                  <datoVedtakTom>2020-02-29</datoVedtakTom>
                  <sats>20</sats>
                  <fradragTillegg>T</fradragTillegg>
                  <typeSats>MND</typeSats>
                  <brukKjoreplan>N</brukKjoreplan>
                  <saksbehId>SU</saksbehId>
                  <utbetalesTilId>$fnr</utbetalesTilId>
                  <attestant-180>
                    <attestantId>A123456</attestantId>
                  </attestant-180>
                </oppdrags-linje-150>
              </oppdrag-110>
            </Oppdrag>
        """.trimIndent()
}
