package no.nav.su.se.bakover.client.oppdrag.utbetaling

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.client.oppdrag.MqPublisher
import no.nav.su.se.bakover.client.oppdrag.MqPublisher.CouldNotPublish
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.domain.Attestant
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.oppdrag.NyUtbetaling
import no.nav.su.se.bakover.domain.oppdrag.Oppdrag
import no.nav.su.se.bakover.domain.oppdrag.Oppdragsmelding
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
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
                    id = UUID30.randomUUID(),
                    opprettet = Tidspunkt.EPOCH,
                    sakId = UUID.randomUUID()
                ),
                utbetaling = Utbetaling.Ny(
                    utbetalingslinjer = emptyList(),
                    fnr = Fnr("12345678910")
                ),
                attestant = Attestant("id")
            )
        )
        mqClient.count shouldBe 1
        res.isLeft() shouldBe true
        res.mapLeft { it.oppdragsmelding.originalMelding shouldBe mqClient.messages.first() }
    }

    @Test
    fun `returnerer xml string hvis ok`() {
        val mqClient = MqPublisherMock(Unit.right())
        val client = UtbetalingMqPublisher(mqClient)

        val res = client.publish(
            nyUtbetaling = NyUtbetaling(
                oppdrag = Oppdrag(
                    id = UUID30.randomUUID(),
                    opprettet = Tidspunkt.EPOCH,
                    sakId = UUID.randomUUID()
                ),
                utbetaling = Utbetaling.Ny(
                    utbetalingslinjer = emptyList(),
                    fnr = Fnr("12345678910")
                ),
                attestant = Attestant("id")
            )
        )
        mqClient.count shouldBe 1
        res.isRight() shouldBe true
        res.map {
            it.originalMelding shouldBe mqClient.messages.first()
            it.status shouldBe Oppdragsmelding.Oppdragsmeldingstatus.SENDT
        }
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
