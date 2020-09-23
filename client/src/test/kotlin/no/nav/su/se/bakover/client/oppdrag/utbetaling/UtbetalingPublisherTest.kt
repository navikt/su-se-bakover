package no.nav.su.se.bakover.client.oppdrag.utbetaling

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.client.oppdrag.MqPublisher
import no.nav.su.se.bakover.client.oppdrag.MqPublisher.CouldNotPublish
import no.nav.su.se.bakover.domain.Attestant
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.oppdrag.NyUtbetaling
import no.nav.su.se.bakover.domain.oppdrag.Oppdrag
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.utbetaling.UtbetalingPublisher
import org.junit.jupiter.api.Test
import java.util.UUID

internal class UtbetalingPublisherTest {

    @Test
    fun `feil skal ikke propageres`() {
        val mqClient = MqPublisherMock(CouldNotPublish.left())
        val client = UtbetalingMqPublisher(mqClient)

        val res = client.publish(
            nyUtbetaling = NyUtbetaling(
                oppdrag = Oppdrag(
                    sakId = UUID.randomUUID()
                ),
                utbetaling = Utbetaling(
                    utbetalingslinjer = emptyList(),
                    fnr = Fnr("12345678910")
                ),
                attestant = Attestant("id")
            )
        )
        mqClient.count shouldBe 1
        res shouldBe UtbetalingPublisher.KunneIkkeSendeUtbetaling(mqClient.messages.first()).left()
    }

    @Test
    fun `returnerer xml string hvis ok`() {
        val mqClient = MqPublisherMock(Unit.right())
        val client = UtbetalingMqPublisher(mqClient)

        val res = client.publish(
            nyUtbetaling = NyUtbetaling(
                oppdrag = Oppdrag(
                    sakId = UUID.randomUUID()
                ),
                utbetaling = Utbetaling(
                    utbetalingslinjer = emptyList(),
                    fnr = Fnr("12345678910")
                ),
                attestant = Attestant("id")
            )
        )
        mqClient.count shouldBe 1
        res shouldBe mqClient.messages.first().right()
    }

    class MqPublisherMock(val response: Either<CouldNotPublish, Unit>) : MqPublisher {
        var count = 0
        var messages: MutableList<String> = mutableListOf()

        override fun publish(vararg messages: String): Either<CouldNotPublish, Unit> {
            ++count
            this.messages.addAll(messages)
            return response
        }
    }
}
