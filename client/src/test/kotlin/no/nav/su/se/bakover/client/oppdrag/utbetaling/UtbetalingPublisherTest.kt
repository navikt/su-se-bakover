package no.nav.su.se.bakover.client.oppdrag.utbetaling

import arrow.core.Either
import arrow.core.left
import arrow.core.nonEmptyListOf
import arrow.core.right
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.client.oppdrag.MqPublisher
import no.nav.su.se.bakover.client.oppdrag.MqPublisher.CouldNotPublish
import no.nav.su.se.bakover.client.oppdrag.avstemming.sakId
import no.nav.su.se.bakover.client.oppdrag.avstemming.saksnummer
import no.nav.su.se.bakover.common.idag
import no.nav.su.se.bakover.common.periode.januar
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Sakstype
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemmingsnøkkel
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.utbetalingslinje
import org.junit.jupiter.api.Test

internal class UtbetalingPublisherTest {

    @Test
    fun `feil skal ikke propageres`() {
        val mqClient = MqPublisherMock(CouldNotPublish.left())
        val client = UtbetalingMqPublisher(mqClient)

        val res = client.publish(utbetaling = simulertUtbetaling)
        mqClient.count shouldBe 1
        res.isLeft() shouldBe true
        res.mapLeft { it.oppdragsmelding.value shouldBe mqClient.messages.first() }
    }

    @Test
    fun `returnerer xml string hvis ok`() {
        val mqClient = MqPublisherMock(Unit.right())
        val client = UtbetalingMqPublisher(mqClient)

        val res = client.publish(utbetaling = simulertUtbetaling)
        mqClient.count shouldBe 1
        res.isRight() shouldBe true
        res.map {
            it.value shouldBe mqClient.messages.first()
        }
    }

    class MqPublisherMock(private val response: Either<CouldNotPublish, Unit>) : MqPublisher {
        var count = 0
        var messages: MutableList<String> = mutableListOf()

        override fun publish(vararg messages: String): Either<CouldNotPublish, Unit> {
            ++count
            this.messages.addAll(messages)
            return response
        }
    }

    private val simulertUtbetaling = Utbetaling.UtbetalingForSimulering(
        opprettet = fixedTidspunkt,
        sakId = sakId,
        saksnummer = saksnummer,
        fnr = Fnr("12345678910"),
        utbetalingslinjer = nonEmptyListOf(
            utbetalingslinje(
                periode = januar(2020),
                beløp = 0,
            ),
        ),
        behandler = NavIdentBruker.Saksbehandler("Z123"),
        avstemmingsnøkkel = Avstemmingsnøkkel(fixedTidspunkt),
        sakstype = Sakstype.UFØRE,
    ).toSimulertUtbetaling(
        simulering = Simulering(
            gjelderId = Fnr(
                fnr = "12345678910",
            ),
            gjelderNavn = "navn",
            datoBeregnet = idag(fixedClock),
            nettoBeløp = 0,
            periodeList = listOf(),
        ),
    )
}
