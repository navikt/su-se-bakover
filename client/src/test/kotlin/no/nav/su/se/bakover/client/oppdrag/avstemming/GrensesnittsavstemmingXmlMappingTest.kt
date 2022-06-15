package no.nav.su.se.bakover.client.oppdrag.avstemming

import no.nav.su.se.bakover.client.oppdrag.XmlMapper
import no.nav.su.se.bakover.client.oppdrag.avstemming.Aksjonsdata.AksjonType.AVSLUTT
import no.nav.su.se.bakover.client.oppdrag.avstemming.Aksjonsdata.AksjonType.DATA
import no.nav.su.se.bakover.client.oppdrag.avstemming.GrensesnittsavstemmingData.Detaljdata
import no.nav.su.se.bakover.client.oppdrag.avstemming.GrensesnittsavstemmingData.Detaljdata.Detaljtype.GODKJENT_MED_VARSEL
import no.nav.su.se.bakover.client.oppdrag.avstemming.GrensesnittsavstemmingData.Grunnlagdata
import org.hamcrest.MatcherAssert
import org.junit.jupiter.api.Test
import org.xmlunit.diff.DefaultNodeMatcher
import org.xmlunit.diff.ElementSelectors
import org.xmlunit.matchers.CompareMatcher
import java.math.BigDecimal

internal class GrensesnittsavstemmingXmlMappingTest {
    private val nodeMatcher = DefaultNodeMatcher().apply { ElementSelectors.byName }

    @Test
    fun `Sjekk mapping av start melding`() {
        val request = AvstemmingStartRequest(
            aksjon = Aksjonsdata.Grensesnittsavstemming(
                nokkelFom = "nokkelFom",
                nokkelTom = "nokkelTom",
                avleverendeAvstemmingId = "avleverendeAvstemmingId",
                underkomponentKode = "SUUFORE",
            ).start(),
        )

        //language=xml
        val expected =
            """
            <?xml version='1.0' encoding='UTF-8'?>
            <avstemmingsdata>
              <aksjon>
                <kildeType>AVLEV</kildeType>
                <mottakendeKomponentKode>OS</mottakendeKomponentKode>
                <brukerId>SU</brukerId>
                <avleverendeKomponentKode>SU</avleverendeKomponentKode>
                <underkomponentKode>SUUFORE</underkomponentKode>
                <aksjonType>START</aksjonType>
                <avleverendeAvstemmingId>avleverendeAvstemmingId</avleverendeAvstemmingId>
                <nokkelFom>nokkelFom</nokkelFom>
                <nokkelTom>nokkelTom</nokkelTom>
                <avstemmingType>GRSN</avstemmingType>
              </aksjon>
            </avstemmingsdata>
            """.trimIndent()

        MatcherAssert.assertThat(
            XmlMapper.writeValueAsString(request),
            CompareMatcher.isSimilarTo(expected).withNodeMatcher(nodeMatcher),
        )
    }

    @Test
    fun `Sjekk mapping av data melding`() {
        val dataRequest = GrensesnittsavstemmingData(
            aksjon = Aksjonsdata.Grensesnittsavstemming(
                aksjonType = DATA,
                nokkelFom = "nokkelFom",
                nokkelTom = "nokkelTom",
                avleverendeAvstemmingId = "avleverendeAvstemmingId",
                underkomponentKode = "SUUFORE",
            ),
            total = Totaldata(
                totalAntall = 1,
                totalBelop = BigDecimal(100),
                fortegn = Fortegn.TILLEGG,
            ),
            periode = Periodedata(
                datoAvstemtFom = "2020090100",
                datoAvstemtTom = "2020090123",
            ),
            grunnlag = Grunnlagdata(
                godkjentAntall = 1,
                godkjentBelop = BigDecimal("100.45"),
                godkjentFortegn = Fortegn.TILLEGG,
                varselAntall = 0,
                varselBelop = BigDecimal.ZERO,
                varselFortegn = Fortegn.TILLEGG,
                avvistAntall = 0,
                avvistBelop = BigDecimal.ZERO,
                avvistFortegn = Fortegn.TILLEGG,
                manglerAntall = 0,
                manglerBelop = BigDecimal.ZERO,
                manglerFortegn = Fortegn.TILLEGG,
            ),
            detalj = listOf(
                Detaljdata(
                    detaljType = GODKJENT_MED_VARSEL,
                    offnr = "12345678901",
                    avleverendeTransaksjonNokkel = "123456789",
                    tidspunkt = "2020-09-02.01.01.01.000000",
                ),
            ),
        )

        val expected =
            """
            <?xml version='1.0' encoding='UTF-8'?>
            <avstemmingsdata>
              <aksjon>
                <kildeType>AVLEV</kildeType>
                <mottakendeKomponentKode>OS</mottakendeKomponentKode>
                <brukerId>SU</brukerId>
                <avleverendeKomponentKode>SU</avleverendeKomponentKode>
                <underkomponentKode>SUUFORE</underkomponentKode>
                <aksjonType>DATA</aksjonType>
                <avleverendeAvstemmingId>avleverendeAvstemmingId</avleverendeAvstemmingId>
                <nokkelFom>nokkelFom</nokkelFom>
                <nokkelTom>nokkelTom</nokkelTom>
                <avstemmingType>GRSN</avstemmingType>
              </aksjon>
              <total>
                <totalAntall>1</totalAntall>
                <totalBelop>100</totalBelop>
                <fortegn>T</fortegn>
              </total>
              <periode>
                <datoAvstemtFom>2020090100</datoAvstemtFom>
                <datoAvstemtTom>2020090123</datoAvstemtTom>
              </periode>
              <grunnlag>
                <godkjentAntall>1</godkjentAntall>
                <godkjentBelop>100.45</godkjentBelop>
                <godkjentFortegn>T</godkjentFortegn>
                <varselAntall>0</varselAntall>
                <varselBelop>0</varselBelop>
                <varselFortegn>T</varselFortegn>
                <avvistAntall>0</avvistAntall>
                <avvistBelop>0</avvistBelop>
                <avvistFortegn>T</avvistFortegn>
                <manglerAntall>0</manglerAntall>
                <manglerBelop>0</manglerBelop>
                <manglerFortegn>T</manglerFortegn>
              </grunnlag>
              <detalj>
                <detaljType>VARS</detaljType>
                <offnr>12345678901</offnr>
                <avleverendeTransaksjonNokkel>123456789</avleverendeTransaksjonNokkel>
                <tidspunkt>2020-09-02.01.01.01.000000</tidspunkt>
              </detalj>
            </avstemmingsdata>
            """.trimIndent()

        MatcherAssert.assertThat(
            XmlMapper.writeValueAsString(dataRequest),
            CompareMatcher.isSimilarTo(expected).withNodeMatcher(nodeMatcher),
        )
    }

    @Test
    fun `Sjekk mapping av stopp melding`() {
        val request = AvstemmingStoppRequest(
            aksjon = Aksjonsdata.Grensesnittsavstemming(
                aksjonType = AVSLUTT,
                nokkelFom = "nokkelFom",
                nokkelTom = "nokkelTom",
                avleverendeAvstemmingId = "avleverendeAvstemmingId",
                underkomponentKode = "SUUFORE",
            ).avslutt(),
        )

        val expected =
            """
            <?xml version='1.0' encoding='UTF-8'?>
            <avstemmingsdata>
              <aksjon>
                <kildeType>AVLEV</kildeType>
                <mottakendeKomponentKode>OS</mottakendeKomponentKode>
                <brukerId>SU</brukerId>
                <avleverendeKomponentKode>SU</avleverendeKomponentKode>
                <underkomponentKode>SUUFORE</underkomponentKode>
                <aksjonType>AVSL</aksjonType>
                <avleverendeAvstemmingId>avleverendeAvstemmingId</avleverendeAvstemmingId>
                <nokkelFom>nokkelFom</nokkelFom>
                <nokkelTom>nokkelTom</nokkelTom>
                <avstemmingType>GRSN</avstemmingType>
              </aksjon>
            </avstemmingsdata>
            """.trimIndent()

        MatcherAssert.assertThat(
            XmlMapper.writeValueAsString(request),
            CompareMatcher.isSimilarTo(expected).withNodeMatcher(nodeMatcher),
        )
    }
}
