package no.nav.su.se.bakover.client.oppdrag.avstemming

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.client.oppdrag.MqPublisher
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.now
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.avstemming.AvstemmingPublisher
import org.junit.jupiter.api.Test
import java.util.UUID

class AvstemmingPublisherTest {

    @Test
    fun `publish avstemming faktisk sender melding på mq`() {
        val utbetalinger = listOf(
            Utbetaling(
                id = UUID30.randomUUID(),
                opprettet = now(),
                behandlingId = UUID.randomUUID(),
                simulering = null,
                kvittering = null,
                oppdragsmelding = null,
                utbetalingslinjer = listOf()
            )
        )

        val client = MqPublisherMock(Unit.right())
        val res = AvstemmingMqPublisher(mqPublisher = client).publish(utbetalinger)

        client.count shouldBe 1
        client.listMessage.size shouldBe 3
        res.isRight() shouldBe true
    }

    @Test
    fun `publish avstemming feiler`() {
        val utbetalinger = listOf(
            Utbetaling(
                id = UUID30.randomUUID(),
                opprettet = now(),
                behandlingId = UUID.randomUUID(),
                simulering = null,
                kvittering = null,
                oppdragsmelding = null,
                utbetalingslinjer = listOf()
            )
        )

        val client = MqPublisherMock(MqPublisher.CouldNotPublish.left())
        val res = AvstemmingMqPublisher(mqPublisher = client).publish(utbetalinger)

        res shouldBe AvstemmingPublisher.KunneIkkeSendeAvstemming.left()
    }

    class MqPublisherMock(val response: Either<MqPublisher.CouldNotPublish, Unit>) : MqPublisher {
        var count = 0
        var listMessage = mutableListOf<String>()

        override fun publish(vararg messages: String): Either<MqPublisher.CouldNotPublish, Unit> {
            ++count
            this.listMessage.addAll(messages)
            return response
        }
    }
}
