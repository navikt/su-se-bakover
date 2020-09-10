package no.nav.su.se.bakover.client.oppdrag.avstemming

import arrow.core.Either
import arrow.core.right
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.client.oppdrag.MqPublisher
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
import no.nav.su.se.bakover.common.april
import no.nav.su.se.bakover.common.juni
import no.nav.su.se.bakover.common.mai
import no.nav.su.se.bakover.common.mars
import no.nav.su.se.bakover.common.now
import no.nav.su.se.bakover.domain.oppdrag.Kvittering
import no.nav.su.se.bakover.domain.oppdrag.Kvittering.Utbetalingsstatus
import no.nav.su.se.bakover.domain.oppdrag.Kvittering.Utbetalingsstatus.OK
import no.nav.su.se.bakover.domain.oppdrag.Kvittering.Utbetalingsstatus.OK_MED_VARSEL
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
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID

class AvstemmingPublisherTest {
    val clock = Clock.fixed(Instant.parse("1970-01-01T00:00:00.000+01:00"), ZoneOffset.UTC)
    private val nodeMatcher = DefaultNodeMatcher().apply { ElementSelectors.byName }

    @Test
    fun `Sjekk at publish klarer å sende riktige antall og beløp`() {
        val mqClient = MqPublisherMock(Unit.right())
        val client = AvstemmingMqPublisher(clock, mqClient)

        val listUtbetaling = mutableListOf<Utbetaling>()

        listUtbetaling.add(
            lagUtbetaling(
                1.mars(2020),
                OK,
                listOf(
                    lagUtbetalingLinje(1.mars(2020), 31.mars(2020), 100),
                    lagUtbetalingLinje(1.april(2020), 30.april(2020), 200)
                )
            )
        )
        listUtbetaling.add(
            lagUtbetaling(
                1.mars(2020),
                OK,
                listOf(
                    lagUtbetalingLinje(1.mars(2020), 31.mars(2020), 600),
                    lagUtbetalingLinje(1.april(2020), 30.april(2020), 700)
                )
            )
        )
        listUtbetaling.add(
            lagUtbetaling(
                2.mars(2020),
                OK_MED_VARSEL,
                listOf(
                    lagUtbetalingLinje(1.mars(2020), 31.mars(2020), 400),
                    lagUtbetalingLinje(1.april(2020), 30.april(2020), 500),
                    lagUtbetalingLinje(1.mai(2020), 31.mai(2020), 500)
                )
            )
        )
        listUtbetaling.add(
            lagUtbetaling(
                1.mars(2020),
                Utbetalingsstatus.FEIL,
                listOf(
                    lagUtbetalingLinje(1.mars(2020), 31.mars(2020), 1000),
                    lagUtbetalingLinje(1.april(2020), 30.april(2020), 2000),
                    lagUtbetalingLinje(1.mai(2020), 31.mai(2020), 3000),
                    lagUtbetalingLinje(1.juni(2020), 30.juni(2020), 4000)
                )
            )
        )

        client.publish(listUtbetaling)
        println(mqClient.listMessage)

        val datoAvstemtFom = "2020030100"
        val datoAvstemtTom = "2020030200"

        val totalAntall = "4"
        val totalBelop = "13000"

        val godkjentAntall = "2"
        val varselAntall = "1"
        val avvistAntall = "1"

        val godkjentBelop = "1600"
        val varselBelop = "1400"
        val avvistBelop = "10000"

        val expected =
            """
            <?xml version='1.0' encoding='UTF-8'?>
            <avstemmingsdata>
              <aksjon>
                <aksjonType>DATA</aksjonType>
                <kildeType>AVLEV</kildeType>
                <avstemmingType>GRSN</avstemmingType>
                <mottakendeKomponentKode>os</mottakendeKomponentKode>
                <brukerId>su</brukerId>
              </aksjon>
              <total>
                <totalAntall>$totalAntall</totalAntall>
                <totalBelop>$totalBelop</totalBelop>
                <fortegn>T</fortegn>
              </total>
              <periode>
                <datoAvstemtFom>$datoAvstemtFom</datoAvstemtFom>
                <datoAvstemtTom>$datoAvstemtTom</datoAvstemtTom>
              </periode>
              <grunnlag>
                <godkjentAntall>$godkjentAntall</godkjentAntall>
                <godkjentBelop>$godkjentBelop</godkjentBelop>
                <godkjenttFortegn>T</godkjenttFortegn>
                <varselAntall>$varselAntall</varselAntall>
                <varselBelop>$varselBelop</varselBelop>
                <varselFortegn>T</varselFortegn>
                <avvistAntall>$avvistAntall</avvistAntall>
                <avvistBelop>$avvistBelop</avvistBelop>
                <avvistFortegn>T</avvistFortegn>
                <manglerAntall>0</manglerAntall>
                <manglerBelop>0</manglerBelop>
                <manglerFortegn>T</manglerFortegn>
              </grunnlag>
            </avstemmingsdata>
            """.trimIndent()

        MatcherAssert.assertThat(
            mqClient.listMessage[1],
            CompareMatcher.isSimilarTo(expected).withNodeMatcher(nodeMatcher)
        )
    }

    @Test
    fun `Sjekk publish start melding`() {
        val mqClient = MqPublisherMock(Unit.right())
        val client = AvstemmingMqPublisher(clock, mqClient)

        val startRequest = AvstemmingStartRequest(
            aksjon = Aksjonsdata(
                aksjonType = START,
                kildeType = AVLEVERT,
                avstemmingType = GRENSESNITTAVSTEMMING,
                mottakendeKomponentKode = "os",
                brukerId = "su"
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
                <mottakendeKomponentKode>os</mottakendeKomponentKode>
                <brukerId>su</brukerId>
              </aksjon>
            </avstemmingsdata>
            """.trimIndent()

        client.publishStart(startRequest) shouldBe Unit.right()

        MatcherAssert.assertThat(
            mqClient.listMessage[0],
            CompareMatcher.isSimilarTo(expected).withNodeMatcher(nodeMatcher)
        )

        mqClient.count shouldBe 1
    }

    @Test
    fun `Sjekk publish stopp melding`() {
        val mqClient = MqPublisherMock(Unit.right())
        val client = AvstemmingMqPublisher(clock, mqClient)

        val stoppRequest = AvstemmingStoppRequest(
            aksjon = Aksjonsdata(
                aksjonType = AVSLUTT,
                kildeType = AVLEVERT,
                avstemmingType = GRENSESNITTAVSTEMMING,
                mottakendeKomponentKode = "os",
                brukerId = "su"
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
                <mottakendeKomponentKode>os</mottakendeKomponentKode>
                <brukerId>su</brukerId>
              </aksjon>
            </avstemmingsdata>
            """.trimIndent()

        client.publishStopp(stoppRequest) shouldBe Unit.right()

        MatcherAssert.assertThat(
            mqClient.listMessage[0],
            CompareMatcher.isSimilarTo(expected).withNodeMatcher(nodeMatcher)
        )

        mqClient.count shouldBe 1
    }

    @Test
    fun `Sjekk publish data melding`() {
        val mqClient = MqPublisherMock(Unit.right())
        val client = AvstemmingMqPublisher(clock, mqClient)

        val dataRequest = AvstemmingDataRequest(
            aksjon = Aksjonsdata(
                aksjonType = DATA,
                kildeType = AVLEVERT,
                avstemmingType = GRENSESNITTAVSTEMMING,
                mottakendeKomponentKode = "os",
                brukerId = "su"
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
                <mottakendeKomponentKode>os</mottakendeKomponentKode>
                <brukerId>su</brukerId>
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

        client.publishData(dataRequest) shouldBe Unit.right()

        println(mqClient.listMessage[0])
        MatcherAssert.assertThat(
            mqClient.listMessage[0],
            CompareMatcher.isSimilarTo(expected).withNodeMatcher(nodeMatcher)
        )

        mqClient.count shouldBe 1
    }

    class MqPublisherMock(val response: Either<MqPublisher.CouldNotPublish, Unit>) : MqPublisher {
        var count = 0
        var listMessage = mutableListOf<String>()

        override fun publish(message: String): Either<MqPublisher.CouldNotPublish, Unit> {
            ++count
            this.listMessage.add(message)
            return response
        }
    }

    private fun lagUtbetalingLinje(fom: LocalDate, tom: LocalDate, beløp: Int) = Utbetalingslinje(
        id = UUID30.randomUUID(),
        opprettet = fom.atStartOfDay(ZoneId.of("Europe/Oslo")).toInstant(),
        fom = fom,
        tom = tom,
        forrigeUtbetalingslinjeId = null,
        beløp = beløp
    )

    private fun lagUtbetaling(opprettet: LocalDate, status: Utbetalingsstatus, linjer: List<Utbetalingslinje>) = Utbetaling(
        id = UUID30.randomUUID(),
        opprettet = opprettet.atStartOfDay(ZoneId.of("Europe/Oslo")).toInstant(),
        behandlingId = UUID.randomUUID(),
        simulering = null,
        kvittering = Kvittering(utbetalingsstatus = status, originalKvittering = "hallo", mottattTidspunkt = now()),
        oppdragsmelding = Oppdragsmelding(SENDT, "Melding"),
        utbetalingslinjer = linjer
    )
}
