package no.nav.su.se.bakover.client.oppdrag.utbetaling

import arrow.core.nonEmptyListOf
import no.nav.su.se.bakover.client.oppdrag.XmlMapper
import no.nav.su.se.bakover.client.oppdrag.avstemming.sakId
import no.nav.su.se.bakover.client.oppdrag.avstemming.saksnummer
import no.nav.su.se.bakover.common.Fnr
import no.nav.su.se.bakover.common.NavIdentBruker
import no.nav.su.se.bakover.common.februar
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.periode.februar
import no.nav.su.se.bakover.common.periode.januar
import no.nav.su.se.bakover.common.startOfDay
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingslinje
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemmingsnøkkel
import no.nav.su.se.bakover.domain.sak.Sakstype
import no.nav.su.se.bakover.test.TikkendeKlokke
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.utbetalingslinje
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

    private val clock = TikkendeKlokke()

    private val førsteUtbetalingsLinje = utbetalingslinje(
        periode = januar(2020),
        beløp = 10,
        opprettet = clock.nextTidspunkt(),
    )
    private val andreUtbetalingslinje = utbetalingslinje(
        periode = februar(2020),
        beløp = 20,
        forrigeUtbetalingslinjeId = førsteUtbetalingsLinje.id,
        uføregrad = 60,
        opprettet = clock.nextTidspunkt(),
    )

    private val tredjeUtbetalingslinje = Utbetalingslinje.Endring.Opphør(
        andreUtbetalingslinje,
        virkningsperiode = Periode.create(1.februar(2020), andreUtbetalingslinje.periode.tilOgMed),
        opprettet = clock.nextTidspunkt(),
    )

    private val fnr = Fnr("12345678910")
    private val utbetaling = Utbetaling.UtbetalingForSimulering(
        opprettet = fixedTidspunkt,
        sakId = sakId,
        saksnummer = saksnummer,
        fnr = fnr,
        utbetalingslinjer = nonEmptyListOf(
            førsteUtbetalingsLinje,
            andreUtbetalingslinje,
            tredjeUtbetalingslinje,
        ),
        behandler = NavIdentBruker.Attestant("A123456"),
        avstemmingsnøkkel = Avstemmingsnøkkel(1.januar(2020).startOfDay()),
        sakstype = Sakstype.UFØRE,
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
                  <henvisning>${utbetaling.id}</henvisning>
                  <grad-170>
                    <typeGrad>UFOR</typeGrad>
                    <grad>50</grad>
                  </grad-170>
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
                  <henvisning>${utbetaling.id}</henvisning>
                  <refFagsystemId>$saksnummer</refFagsystemId>
                  <refDelytelseId>${førsteUtbetalingsLinje.id}</refDelytelseId>
                  <grad-170>
                    <typeGrad>UFOR</typeGrad>
                    <grad>60</grad>
                  </grad-170>
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
                  <henvisning>${utbetaling.id}</henvisning>
                  <grad-170>
                    <typeGrad>UFOR</typeGrad>
                    <grad>60</grad>
                  </grad-170>
                  <attestant-180>
                    <attestantId>A123456</attestantId>
                  </attestant-180>
                </oppdrags-linje-150>
              </oppdrag-110>
            </Oppdrag>
        """.trimIndent()
}
