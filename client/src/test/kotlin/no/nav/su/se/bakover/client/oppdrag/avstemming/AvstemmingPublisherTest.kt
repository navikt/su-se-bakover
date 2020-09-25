package no.nav.su.se.bakover.client.oppdrag.avstemming

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.client.oppdrag.MqPublisher
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.toMicroInstant
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.oppdrag.Oppdragsmelding
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemming
import no.nav.su.se.bakover.domain.oppdrag.avstemming.AvstemmingPublisher
import org.junit.jupiter.api.Test

class AvstemmingPublisherTest {

    @Test
    fun `publish avstemming faktisk sender melding på mq`() {
        val client = MqPublisherMock(Unit.right())
        val res = AvstemmingMqPublisher(mqPublisher = client)
            .publish(avstemming)

        client.count shouldBe 1
        client.publishedMessages.size shouldBe 3
        res.isRight() shouldBe true
    }

    @Test
    fun `publish avstemming feiler`() {
        val client = MqPublisherMock(MqPublisher.CouldNotPublish.left())
        val res = AvstemmingMqPublisher(mqPublisher = client)
            .publish(avstemming)

        res shouldBe AvstemmingPublisher.KunneIkkeSendeAvstemming.left()
    }

    private val avstemming = Avstemming(
        fom = 1.januar(2020).atStartOfDay().toMicroInstant(),
        tom = 2.januar(2020).atStartOfDay().toMicroInstant(),
        utbetalinger = listOf(
            Utbetaling(
                utbetalingslinjer = listOf(),
                fnr = Fnr("12345678910"),
                oppdragsmelding = Oppdragsmelding(Oppdragsmelding.Oppdragsmeldingstatus.SENDT, "")
            )
        )
    )

    class MqPublisherMock(val response: Either<MqPublisher.CouldNotPublish, Unit>) : MqPublisher {
        var count = 0
        var publishedMessages = mutableListOf<String>()

        override fun publish(vararg messages: String): Either<MqPublisher.CouldNotPublish, Unit> {
            ++count
            this.publishedMessages.addAll(messages)
            return response
        }
    }
}
