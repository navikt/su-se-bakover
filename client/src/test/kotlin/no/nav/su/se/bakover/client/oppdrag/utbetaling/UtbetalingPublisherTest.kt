package no.nav.su.se.bakover.client.oppdrag.utbetaling

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.client.oppdrag.MqPublisher
import no.nav.su.se.bakover.client.oppdrag.MqPublisher.CouldNotPublish
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.februar
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingslinje
import no.nav.su.se.bakover.domain.oppdrag.utbetaling.UtbetalingPublisher.KunneIkkeSendeUtbetaling
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

internal class UtbetalingPublisherTest {

    val clock = Clock.fixed(Instant.parse("1970-01-01T00:00:00.000+01:00"), ZoneOffset.UTC)
    val førsteUtbetalingsLinje = Utbetalingslinje(
        fom = 1.januar(2020),
        tom = 31.januar(2020),
        beløp = 10,
        forrigeUtbetalingslinjeId = null
    )
    val andreUtbetalingslinje = Utbetalingslinje(
        fom = 1.februar(2020),
        tom = 29.februar(2020),
        beløp = 20,
        forrigeUtbetalingslinjeId = førsteUtbetalingsLinje.id
    )
    val utbetaling = Utbetaling(
        behandlingId = UUID.randomUUID(),
        utbetalingslinjer = listOf(
            førsteUtbetalingsLinje,
            andreUtbetalingslinje
        ),
        oppdragId = UUID30.randomUUID()
    )

    @Test
    fun `feil skal ikke propageres`() {
        val mqClient = MqPublisherMock(CouldNotPublish.left())
        val client = UtbetalingMqPublisher(clock, mqClient)
        client.publish(utbetaling, "Saksbehandler") shouldBe KunneIkkeSendeUtbetaling.left()
        mqClient.count shouldBe 1
    }

    @Test
    fun `verifiser xml request`() {
        val mqClient = MqPublisherMock(Unit.right())

        val client = UtbetalingMqPublisher(clock, mqClient)

        client.publish(utbetaling, "Saksbehandler") shouldBe Unit.right()
        mqClient.count shouldBe 1
        val expected =
            """
            <?xml version='1.0' encoding='UTF-8'?>
            <Oppdrag>
              <oppdrag-110>
                <kodeAksjon>1</kodeAksjon>
                <kodeEndring>NY</kodeEndring>
                <kodeFagomraade>SUUFORE</kodeFagomraade>
                <fagsystemId>${utbetaling.oppdragId}</fagsystemId>
                <utbetFrekvens>MND</utbetFrekvens>
                <oppdragGjelderId>Saksbehandler</oppdragGjelderId>
                <datoOppdragGjelderFom>1970-01-01</datoOppdragGjelderFom>
                <saksbehId>SU</saksbehId>
                <avstemming-115>
                  <kodeKomponent>SUUFORE</kodeKomponent>
                  <nokkelAvstemming>${utbetaling.id}</nokkelAvstemming>
                  <tidspktMelding>1970-01-01-00.00.00.000000</tidspktMelding>
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
                  <utbetalesTilId>Saksbehandler</utbetalesTilId>
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
                  <utbetalesTilId>Saksbehandler</utbetalesTilId>
                  <refDelytelseId>${førsteUtbetalingsLinje.id}</refDelytelseId>
                  <refFagsystemId>${utbetaling.oppdragId}</refFagsystemId>
                </oppdrags-linje-150>
              </oppdrag-110>
            </Oppdrag>
            """.trimIndent()
        mqClient.message?.trimIndent() shouldBe expected
    }

    class MqPublisherMock(val response: Either<CouldNotPublish, Unit>) : MqPublisher {
        var count = 0
        var message: String? = null
        override fun publish(message: String): Either<CouldNotPublish, Unit> {
            ++count
            this.message = message
            return response
        }
    }
}
