package no.nav.su.se.bakover.client.oppdrag.avstemming

import no.nav.su.se.bakover.client.oppdrag.avstemming.Aksjonsdata.AksjonType.AVSLUTT
import no.nav.su.se.bakover.client.oppdrag.avstemming.Aksjonsdata.AksjonType.DATA
import no.nav.su.se.bakover.client.oppdrag.avstemming.Aksjonsdata.AksjonType.START
import no.nav.su.se.bakover.client.oppdrag.avstemming.Aksjonsdata.AvstemmingType.GRENSESNITTAVSTEMMING
import no.nav.su.se.bakover.client.oppdrag.avstemming.Aksjonsdata.KildeType.AVLEVERT
import no.nav.su.se.bakover.client.oppdrag.avstemming.AvstemmingDataRequest.Detaljdata
import no.nav.su.se.bakover.client.oppdrag.avstemming.AvstemmingDataRequest.Detaljdata.Detaljtype.GODKJENT_MED_VARSEL
import no.nav.su.se.bakover.client.oppdrag.avstemming.AvstemmingDataRequest.Fortegn.TILLEGG
import no.nav.su.se.bakover.client.oppdrag.avstemming.AvstemmingDataRequest.Grunnlagdata
import no.nav.su.se.bakover.client.oppdrag.avstemming.AvstemmingDataRequest.Periodedata
import no.nav.su.se.bakover.client.oppdrag.avstemming.AvstemmingDataRequest.Totaldata
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.now
import no.nav.su.se.bakover.domain.oppdrag.Kvittering
import no.nav.su.se.bakover.domain.oppdrag.Kvittering.Utbetalingsstatus
import no.nav.su.se.bakover.domain.oppdrag.Oppdragsmelding
import no.nav.su.se.bakover.domain.oppdrag.Oppdragsmelding.Oppdragsmeldingstatus.SENDT
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingslinje
import org.hamcrest.MatcherAssert
import org.junit.jupiter.api.Test
import org.xmlunit.diff.DefaultNodeMatcher
import org.xmlunit.diff.ElementSelectors
import org.xmlunit.matchers.CompareMatcher
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

internal class AvstemmingXmlMapperTest {
    private val nodeMatcher = DefaultNodeMatcher().apply { ElementSelectors.byName }

    @Test
    fun `Sjekk mapping av start melding`() {
        val request = AvstemmingStartRequest(
            aksjon = Aksjonsdata(
                aksjonType = START,
                kildeType = AVLEVERT,
                avstemmingType = GRENSESNITTAVSTEMMING,
                mottakendeKomponentKode = "OS",
                brukerId = "SU"
            )
        )

        val expected =
            """
            <?xml version='1.0' encoding='UTF-8'?>
            <avstemmingsdata>
              <aksjon>
                <aksjonType>START</aksjonType>
                <kildeType>AVLEV</kildeType>
                <avstemmingType>GRSN</avstemmingType>
                <mottakendeKomponentKode>OS</mottakendeKomponentKode>
                <brukerId>SU</brukerId>
                <avleverendeKomponentKode>SU</avleverendeKomponentKode>
                <underkomponentKode>SUUFORE</underkomponentKode>
              </aksjon>
            </avstemmingsdata>
            """.trimIndent()

        MatcherAssert.assertThat(
            AvstemmingXmlMapper.map(request),
            CompareMatcher.isSimilarTo(expected).withNodeMatcher(nodeMatcher)
        )
    }

    @Test
    fun `Sjekk mapping av data melding`() {
        val dataRequest = AvstemmingDataRequest(
            aksjon = Aksjonsdata(
                aksjonType = DATA,
                kildeType = AVLEVERT,
                avstemmingType = GRENSESNITTAVSTEMMING,
                mottakendeKomponentKode = "OS",
                brukerId = "SU"
            ),
            total = Totaldata(
                totalAntall = 1,
                totalBelop = BigDecimal(100),
                fortegn = TILLEGG
            ),
            periode = Periodedata(
                datoAvstemtFom = "2020090100",
                datoAvstemtTom = "2020090123"
            ),
            grunnlag = Grunnlagdata(
                godkjentAntall = 1,
                godkjentBelop = BigDecimal("100.45"),
                godkjenttFortegn = TILLEGG,
                varselAntall = 0,
                varselBelop = BigDecimal(0),
                varselFortegn = TILLEGG,
                avvistAntall = 0,
                avvistBelop = BigDecimal(0),
                avvistFortegn = TILLEGG,
                manglerAntall = 0,
                manglerBelop = BigDecimal(0),
                manglerFortegn = TILLEGG
            ),
            detalj = listOf(
                Detaljdata(
                    detaljType = GODKJENT_MED_VARSEL,
                    offnr = "12345678901",
                    avleverendeTransaksjonNokkel = "123456789",
                    tidspunkt = "2020-09-02.01.01.01.000000"
                )
            )
        )

        val expected =
            """
            <?xml version='1.0' encoding='UTF-8'?>
            <avstemmingsdata>
              <aksjon>
                <aksjonType>DATA</aksjonType>
                <kildeType>AVLEV</kildeType>
                <avstemmingType>GRSN</avstemmingType>
                <mottakendeKomponentKode>OS</mottakendeKomponentKode>
                <brukerId>SU</brukerId>
                <avleverendeKomponentKode>SU</avleverendeKomponentKode>
                <underkomponentKode>SUUFORE</underkomponentKode>
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
                <godkjenttFortegn>T</godkjenttFortegn>
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
            AvstemmingXmlMapper.map(dataRequest),
            CompareMatcher.isSimilarTo(expected).withNodeMatcher(nodeMatcher)
        )
    }

    @Test
    fun `Sjekk mapping av stopp melding`() {
        val request = AvstemmingStoppRequest(
            aksjon = Aksjonsdata(
                aksjonType = AVSLUTT,
                kildeType = AVLEVERT,
                avstemmingType = GRENSESNITTAVSTEMMING,
                mottakendeKomponentKode = "OS",
                brukerId = "SU"
            )
        )

        val expected =
            """
            <?xml version='1.0' encoding='UTF-8'?>
            <avstemmingsdata>
              <aksjon>
                <aksjonType>AVSL</aksjonType>
                <kildeType>AVLEV</kildeType>
                <avstemmingType>GRSN</avstemmingType>
                <mottakendeKomponentKode>OS</mottakendeKomponentKode>
                <brukerId>SU</brukerId>
                <avleverendeKomponentKode>SU</avleverendeKomponentKode>
                <underkomponentKode>SUUFORE</underkomponentKode>
              </aksjon>
            </avstemmingsdata>
            """.trimIndent()

        MatcherAssert.assertThat(
            AvstemmingXmlMapper.map(request),
            CompareMatcher.isSimilarTo(expected).withNodeMatcher(nodeMatcher)
        )
    }

    private fun lagUtbetalingLinje(fom: LocalDate, tom: LocalDate, beløp: Int) = Utbetalingslinje(
        id = UUID30.randomUUID(),
        opprettet = fom.atStartOfDay(ZoneId.of("Europe/Oslo")).toInstant(),
        fom = fom,
        tom = tom,
        forrigeUtbetalingslinjeId = null,
        beløp = beløp
    )

    private fun lagUtbetaling(opprettet: LocalDate, status: Utbetalingsstatus, linjer: List<Utbetalingslinje>) =
        Utbetaling(
            id = UUID30.randomUUID(),
            opprettet = opprettet.atStartOfDay(ZoneId.of("Europe/Oslo")).toInstant(),
            behandlingId = UUID.randomUUID(),
            simulering = null,
            kvittering = Kvittering(utbetalingsstatus = status, originalKvittering = "hallo", mottattTidspunkt = now()),
            oppdragsmelding = Oppdragsmelding(SENDT, "Melding"),
            utbetalingslinjer = linjer
        )
}
