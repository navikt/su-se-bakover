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
import org.hamcrest.MatcherAssert
import org.junit.jupiter.api.Test
import org.xmlunit.diff.DefaultNodeMatcher
import org.xmlunit.diff.ElementSelectors
import org.xmlunit.matchers.CompareMatcher
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class AvstemmingPublisherTest {
    val clock = Clock.fixed(Instant.parse("1970-01-01T00:00:00.000+01:00"), ZoneOffset.UTC)
    private val nodeMatcher = DefaultNodeMatcher().apply { ElementSelectors.byName }

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

        val expected = """
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
            mqClient.message,
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

        val expected = """
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
            mqClient.message,
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

        val expected = """
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

        println(mqClient.message)
        MatcherAssert.assertThat(
            mqClient.message,
            CompareMatcher.isSimilarTo(expected).withNodeMatcher(nodeMatcher)
        )

        mqClient.count shouldBe 1
    }

    class MqPublisherMock(val response: Either<MqPublisher.CouldNotPublish, Unit>) : MqPublisher {
        var count = 0
        var message: String? = null
        override fun publish(message: String): Either<MqPublisher.CouldNotPublish, Unit> {
            ++count
            this.message = message
            return response
        }
    }
}
