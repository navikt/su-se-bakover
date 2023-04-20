package no.nav.su.se.bakover.client.oppdrag.utbetaling

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.client.oppdrag.MqPublisher
import no.nav.su.se.bakover.client.oppdrag.MqPublisher.CouldNotPublish
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.søknadsbehandlingTilAttesteringInnvilget
import no.nav.su.se.bakover.test.utbetaling.nyUtbetalingSimulert
import org.junit.jupiter.api.Test

internal class UtbetalingPublisherTest {

    private val utbetaling = søknadsbehandlingTilAttesteringInnvilget().let { (sak, tilAttestering) ->
        nyUtbetalingSimulert(
            sakOgBehandling = sak to tilAttestering,
            beregning = tilAttestering.beregning,
            clock = fixedClock,
        )
    }

    @Test
    fun `feil skal ikke propageres`() {
        val mqClient = MqPublisherMock(CouldNotPublish.left())
        val client = UtbetalingMqPublisher(mqClient)

        val res = client.publishRequest(client.generateRequest(utbetaling))
        mqClient.count shouldBe 1
        res.isLeft() shouldBe true
        res.mapLeft { it.oppdragsmelding.value shouldBe mqClient.messages.first() }
    }

    @Test
    fun `returnerer xml string hvis ok`() {
        val mqClient = MqPublisherMock(Unit.right())
        val client = UtbetalingMqPublisher(mqClient)

        val res = client.publishRequest(client.generateRequest(utbetaling))
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
}
