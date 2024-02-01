package no.nav.su.se.bakover.client.oppdrag.avstemming

import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.extensions.januar
import no.nav.su.se.bakover.common.extensions.startOfDay
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemming
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.utbetaling.oversendtUtbetalingUtenKvittering
import no.nav.su.se.bakover.test.xml.shouldBeSimilarXmlTo
import org.junit.jupiter.api.Test
import økonomi.domain.Fagområde

internal class KonsistensavstemmingXmlMappingTest {

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
                    <aksjonType>START</aksjonType>
                    <kildeType>AVLEV</kildeType>
                    <avstemmingType>KONS</avstemmingType>
                    <avleverendeKomponentKode>SU</avleverendeKomponentKode>
                    <mottakendeKomponentKode>OS</mottakendeKomponentKode>
                    <underkomponentKode>SUUFORE</underkomponentKode>
                    <tidspunktAvstemmingTom>2021-01-01-02.02.03.456789</tidspunktAvstemmingTom>
                    <avleverendeAvstemmingId>${avstemming.id}</avleverendeAvstemmingId>
                    <brukerId>SU</brukerId>
                  </aksjonsdata>
                </konsistensavstemmingsdata>
              </request>
            </sendAsynkronKonsistensavstemmingsdata>
            """.trimIndent()

        requestBuilder.startXml() shouldBeSimilarXmlTo expected
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
                    <aksjonType>DATA</aksjonType>
                    <kildeType>AVLEV</kildeType>
                    <avstemmingType>KONS</avstemmingType>
                    <avleverendeKomponentKode>SU</avleverendeKomponentKode>
                    <mottakendeKomponentKode>OS</mottakendeKomponentKode>
                    <underkomponentKode>SUUFORE</underkomponentKode>
                    <tidspunktAvstemmingTom>2021-01-01-02.02.03.456789</tidspunktAvstemmingTom>
                    <avleverendeAvstemmingId>${avstemming.id}</avleverendeAvstemmingId>
                    <brukerId>SU</brukerId>
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

        requestBuilder.dataXml()[0] shouldBeSimilarXmlTo expected1
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
                    <aksjonType>AVSL</aksjonType>
                    <kildeType>AVLEV</kildeType>
                    <avstemmingType>KONS</avstemmingType>
                    <avleverendeKomponentKode>SU</avleverendeKomponentKode>
                    <mottakendeKomponentKode>OS</mottakendeKomponentKode>
                    <underkomponentKode>SUUFORE</underkomponentKode>
                    <tidspunktAvstemmingTom>2021-01-01-02.02.03.456789</tidspunktAvstemmingTom>
                    <avleverendeAvstemmingId>${avstemming.id}</avleverendeAvstemmingId>
                    <brukerId>SU</brukerId>
                  </aksjonsdata>
                </konsistensavstemmingsdata>
              </request>
            </sendAsynkronKonsistensavstemmingsdata>
            """.trimIndent()

        requestBuilder.avsluttXml() shouldBeSimilarXmlTo expected
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
                    <aksjonType>DATA</aksjonType>
                    <kildeType>AVLEV</kildeType>
                    <avstemmingType>KONS</avstemmingType>
                    <avleverendeKomponentKode>SU</avleverendeKomponentKode>
                    <mottakendeKomponentKode>OS</mottakendeKomponentKode>
                    <underkomponentKode>SUUFORE</underkomponentKode>
                    <tidspunktAvstemmingTom>2021-01-01-02.02.03.456789</tidspunktAvstemmingTom>
                    <avleverendeAvstemmingId>${avstemming.id}</avleverendeAvstemmingId>
                    <brukerId>SU</brukerId>
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

        requestBuilder.totaldataXml() shouldBeSimilarXmlTo expected
    }
}
