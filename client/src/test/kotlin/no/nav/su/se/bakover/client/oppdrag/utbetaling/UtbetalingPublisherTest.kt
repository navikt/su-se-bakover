package no.nav.su.se.bakover.client.oppdrag.utbetaling

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.client.oppdrag.MqPublisher
import no.nav.su.se.bakover.client.oppdrag.MqPublisher.CouldNotPublish
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.idag
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.oppdrag.OversendelseTilOppdrag
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import org.junit.jupiter.api.Test

internal class UtbetalingPublisherTest {

    @Test
    fun `feil skal ikke propageres`() {
        val mqClient = MqPublisherMock(CouldNotPublish.left())
        val client = UtbetalingMqPublisher(mqClient)

        val res = client.publish(
            tilUtbetaling = OversendelseTilOppdrag.TilUtbetaling(
                utbetaling = simulertUtbetaling
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
            tilUtbetaling = OversendelseTilOppdrag.TilUtbetaling(
                utbetaling = simulertUtbetaling
            )
        )
        mqClient.count shouldBe 1
        res.isRight() shouldBe true
        res.map {
            it.originalMelding shouldBe mqClient.messages.first()
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

    private val simulertUtbetaling = Utbetaling.SimulertUtbetaling(
        fnr = Fnr("12345678910"),
        utbetalingslinjer = listOf(),
        type = Utbetaling.UtbetalingsType.NY,
        simulering = Simulering(
            gjelderId = Fnr(
                fnr = "12345678910"
            ),
            gjelderNavn = "navn", datoBeregnet = idag(), nettoBeløp = 0, periodeList = listOf()
        ),
        oppdragId = UUID30.randomUUID(),
        behandler = NavIdentBruker.Saksbehandler("Z123")
    )
}
