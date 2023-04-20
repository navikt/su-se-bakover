package no.nav.su.se.bakover.client.oppdrag.avstemming

import no.nav.su.se.bakover.common.Fnr
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.startOfDay
import no.nav.su.se.bakover.domain.oppdrag.Fagområde
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemming
import no.nav.su.se.bakover.domain.sak.Saksnummer
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.utbetaling.oversendtUtbetalingUtenKvittering
import org.hamcrest.MatcherAssert
import org.junit.jupiter.api.Test
import org.xmlunit.diff.DefaultNodeMatcher
import org.xmlunit.diff.ElementSelectors
import org.xmlunit.matchers.CompareMatcher

internal class KonsistensavstemmingXmlMappingTest {
    private val nodeMatcher = DefaultNodeMatcher().apply { ElementSelectors.byName }

    private val utbetaling = oversendtUtbetalingUtenKvittering(
        fnr = Fnr("88888888888"),
        saksnummer = Saksnummer(8888),
    )
    private val avstemming = Avstemming.Konsistensavstemming.Ny(
        id = UUID30.randomUUID(),
        opprettet = fixedTidspunkt,
        løpendeFraOgMed = 1.januar(2021).startOfDay(),
        opprettetTilOgMed = fixedTidspunkt,
        utbetalinger = listOf(
            utbetaling,
        ),
        avstemmingXmlRequest = null,
        fagområde = Fagområde.SUUFORE,
    )

    private val requestBuilder = KonsistensavstemmingRequestBuilder(avstemming = avstemming)

    @Test
    fun `Sjekk mapping av start melding`() {
        //language=xml
        val expected =
            """
            <?xml version='1.0' encoding='UTF-8'?>
            <sendAsynkronKonsistensavstemmingsdata>
              <request>
                <konsistensavstemmingsdata>
                  <aksjonsdata>
                    <kildeType>AVLEV</kildeType>
                    <mottakendeKomponentKode>OS</mottakendeKomponentKode>
                    <brukerId>SU</brukerId>
                    <avleverendeKomponentKode>SU</avleverendeKomponentKode>
                    <underkomponentKode>SUUFORE</underkomponentKode>
                    <aksjonType>START</aksjonType>
                    <avleverendeAvstemmingId>${avstemming.id}</avleverendeAvstemmingId>
                    <tidspunktAvstemmingTom>2021-01-01-02.02.03.456789</tidspunktAvstemmingTom>
                    <avstemmingType>KONS</avstemmingType>
                  </aksjonsdata>
                </konsistensavstemmingsdata>
              </request>
            </sendAsynkronKonsistensavstemmingsdata>
            """.trimIndent()

        MatcherAssert.assertThat(
            requestBuilder.startXml(),
            CompareMatcher.isSimilarTo(expected).withNodeMatcher(nodeMatcher),
        )
    }

    @Test
    fun `Sjekk mapping av data melding`() {
        //language=xml
        val expected1 =
            """
            <?xml version='1.0' encoding='UTF-8'?>
            <sendAsynkronKonsistensavstemmingsdata>
              <request>
                <konsistensavstemmingsdata>
                  <aksjonsdata>
                    <kildeType>AVLEV</kildeType>
                    <mottakendeKomponentKode>OS</mottakendeKomponentKode>
                    <brukerId>SU</brukerId>
                    <avleverendeKomponentKode>SU</avleverendeKomponentKode>
                    <underkomponentKode>SUUFORE</underkomponentKode>
                    <aksjonType>DATA</aksjonType>
                    <avleverendeAvstemmingId>${avstemming.id}</avleverendeAvstemmingId>
                    <tidspunktAvstemmingTom>2021-01-01-02.02.03.456789</tidspunktAvstemmingTom>
                    <avstemmingType>KONS</avstemmingType>
                  </aksjonsdata>
                  <oppdragsdataListe>
                    <fagomradeKode>SUUFORE</fagomradeKode>
                    <fagsystemId>8888</fagsystemId>
                    <utbetalingsfrekvens>MND</utbetalingsfrekvens>
                    <oppdragGjelderId>88888888888</oppdragGjelderId>
                    <oppdragGjelderFom>1970-01-01</oppdragGjelderFom>
                    <saksbehandlerId>SU</saksbehandlerId>
                    <oppdragsenhetListe>
                      <enhetType>BOS</enhetType>
                      <enhet>8020</enhet>
                      <enhetFom>1970-01-01</enhetFom>
                    </oppdragsenhetListe>
                    <oppdragslinjeListe>
                      <delytelseId>${utbetaling.utbetalingslinjer[0].id}</delytelseId>
                      <klassifikasjonKode>SUUFORE</klassifikasjonKode>
                      <vedtakPeriode>
                        <fom>2021-01-01</fom>
                        <tom>2021-12-31</tom>
                      </vedtakPeriode>
                      <sats>15000</sats>
                      <satstypeKode>MND</satstypeKode>
                      <fradragTillegg>T</fradragTillegg>
                      <brukKjoreplan>N</brukKjoreplan>
                      <utbetalesTilId>88888888888</utbetalesTilId>
                      <attestantListe>
                        <attestantId>attestant</attestantId>
                      </attestantListe>
                    </oppdragslinjeListe>
                  </oppdragsdataListe>
                </konsistensavstemmingsdata>
              </request>
            </sendAsynkronKonsistensavstemmingsdata>
            """.trimIndent()

        MatcherAssert.assertThat(
            requestBuilder.dataXml()[0],
            CompareMatcher.isSimilarTo(expected1).withNodeMatcher(nodeMatcher),
        )
    }

    @Test
    fun `Sjekk mapping av stopp melding`() {
        //language=xml
        val expected =
            """
            <?xml version='1.0' encoding='UTF-8'?>
            <sendAsynkronKonsistensavstemmingsdata>
              <request>
                <konsistensavstemmingsdata>
                  <aksjonsdata>
                    <kildeType>AVLEV</kildeType>
                    <mottakendeKomponentKode>OS</mottakendeKomponentKode>
                    <brukerId>SU</brukerId>
                    <avleverendeKomponentKode>SU</avleverendeKomponentKode>
                    <underkomponentKode>SUUFORE</underkomponentKode>
                    <aksjonType>AVSL</aksjonType>
                    <avleverendeAvstemmingId>${avstemming.id}</avleverendeAvstemmingId>
                    <tidspunktAvstemmingTom>2021-01-01-02.02.03.456789</tidspunktAvstemmingTom>
                    <avstemmingType>KONS</avstemmingType>
                  </aksjonsdata>
                </konsistensavstemmingsdata>
              </request>
            </sendAsynkronKonsistensavstemmingsdata>
            """.trimIndent()

        MatcherAssert.assertThat(
            requestBuilder.avsluttXml(),
            CompareMatcher.isSimilarTo(expected).withNodeMatcher(nodeMatcher),
        )
    }

    @Test
    fun `Sjekk mapping av totaldata`() {
        //language=xml
        val expected =
            """
            <?xml version='1.0' encoding='UTF-8'?>
            <sendAsynkronKonsistensavstemmingsdata>
              <request>
                <konsistensavstemmingsdata>
                  <aksjonsdata>
                    <kildeType>AVLEV</kildeType>
                    <mottakendeKomponentKode>OS</mottakendeKomponentKode>
                    <brukerId>SU</brukerId>
                    <avleverendeKomponentKode>SU</avleverendeKomponentKode>
                    <underkomponentKode>SUUFORE</underkomponentKode>
                    <aksjonType>DATA</aksjonType>
                    <avleverendeAvstemmingId>${avstemming.id}</avleverendeAvstemmingId>
                    <tidspunktAvstemmingTom>2021-01-01-02.02.03.456789</tidspunktAvstemmingTom>
                    <avstemmingType>KONS</avstemmingType>
                  </aksjonsdata>
                  <totaldata>
                    <totalAntall>1</totalAntall>
                    <totalBelop>15000</totalBelop>
                    <fortegn>T</fortegn>
                  </totaldata>
                </konsistensavstemmingsdata>
              </request>
            </sendAsynkronKonsistensavstemmingsdata>
            """.trimIndent()

        MatcherAssert.assertThat(
            requestBuilder.totaldataXml(),
            CompareMatcher.isSimilarTo(expected).withNodeMatcher(nodeMatcher),
        )
    }
}
