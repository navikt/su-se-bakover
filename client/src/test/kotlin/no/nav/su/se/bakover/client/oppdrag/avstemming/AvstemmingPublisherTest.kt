package no.nav.su.se.bakover.client.oppdrag.avstemming

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.client.oppdrag.MqPublisher
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.idag
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.now
import no.nav.su.se.bakover.common.toTidspunkt
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.oppdrag.Kvittering
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingsrequest
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemming
import no.nav.su.se.bakover.domain.oppdrag.avstemming.AvstemmingPublisher
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
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
        fraOgMed = 1.januar(2020).atStartOfDay().toTidspunkt(),
        tilOgMed = 2.januar(2020).atStartOfDay().toTidspunkt(),
        utbetalinger = listOf(
            Utbetaling.OversendtUtbetaling.MedKvittering(
                utbetalingslinjer = listOf(),
                fnr = Fnr("12345678910"),
                simulering = Simulering(
                    gjelderId = Fnr("12345678910"),
                    gjelderNavn = "",
                    datoBeregnet = idag(),
                    nettoBeløp = 0.0,
                    periodeList = listOf()
                ),
                utbetalingsrequest = Utbetalingsrequest(
                    value = ""
                ),
                kvittering = Kvittering(
                    utbetalingsstatus = Kvittering.Utbetalingsstatus.OK,
                    originalKvittering = "hallo",
                    mottattTidspunkt = now()
                ),
                type = Utbetaling.UtbetalingsType.NY,
                oppdragId = UUID30.randomUUID(),
                behandler = NavIdentBruker.Saksbehandler("Z123")
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
