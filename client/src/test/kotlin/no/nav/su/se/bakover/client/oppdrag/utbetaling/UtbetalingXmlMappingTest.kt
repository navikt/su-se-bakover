package no.nav.su.se.bakover.client.oppdrag.utbetaling

import arrow.core.nonEmptyListOf
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.client.oppdrag.avstemming.sakId
import no.nav.su.se.bakover.client.oppdrag.avstemming.saksnummer
import no.nav.su.se.bakover.common.Rekkefølge
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.extensions.februar
import no.nav.su.se.bakover.common.extensions.januar
import no.nav.su.se.bakover.common.extensions.startOfDay
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.infrastructure.xml.xmlMapper
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.common.tid.periode.februar
import no.nav.su.se.bakover.common.tid.periode.januar
import no.nav.su.se.bakover.test.TikkendeKlokke
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.utbetaling.utbetalingslinjeNy
import no.nav.su.se.bakover.test.xml.shouldBeSimilarXmlTo
import org.junit.jupiter.api.Test
import økonomi.domain.avstemming.Avstemmingsnøkkel
import økonomi.domain.utbetaling.Utbetaling
import økonomi.domain.utbetaling.Utbetalingslinje

class UtbetalingXmlMappingTest {

    @Test
    fun `mapper utbetaling til xml request`() {
        xmlMapper.writeValueAsString(toUtbetalingRequest(utbetaling = utbetaling)).let {
            it.shouldBeSimilarXmlTo(expected, strict = true)
            it.filterNot { it.isWhitespace() } shouldBe expected.filterNot { it.isWhitespace() }
        }
    }

    private val clock = TikkendeKlokke()

    private val rekkefølge = Rekkefølge.generator()
    private val førsteUtbetalingsLinje = utbetalingslinjeNy(
        periode = januar(2020),
        beløp = 10,
        opprettet = clock.nextTidspunkt(),
        rekkefølge = rekkefølge.neste(),
    )
    private val andreUtbetalingslinje = utbetalingslinjeNy(
        periode = februar(2020),
        beløp = 20,
        forrigeUtbetalingslinjeId = førsteUtbetalingsLinje.id,
        uføregrad = 60,
        opprettet = clock.nextTidspunkt(),
        rekkefølge = rekkefølge.neste(),
    )

    private val tredjeUtbetalingslinje = Utbetalingslinje.Endring.Opphør(
        utbetalingslinjeSomSkalEndres = andreUtbetalingslinje,
        virkningsperiode = Periode.create(1.februar(2020), andreUtbetalingslinje.periode.tilOgMed),
        opprettet = clock.nextTidspunkt(),
        rekkefølge = rekkefølge.neste(),
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
