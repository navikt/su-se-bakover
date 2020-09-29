package no.nav.su.se.bakover.web.services.utbetaling.kvittering

import arrow.core.left
import arrow.core.right
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.now
import no.nav.su.se.bakover.domain.oppdrag.Kvittering
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.service.utbetaling.FantIkkeUtbetaling
import no.nav.su.se.bakover.service.utbetaling.UtbetalingService
import no.nav.su.se.bakover.web.FnrGenerator
import no.nav.su.se.bakover.web.services.utbetaling.kvittering.UtbetalingKvitteringResponseTest.Companion.kvitteringXml
import org.junit.jupiter.api.Test
import org.mockito.internal.verification.Times
import java.time.Clock
import java.time.ZoneOffset

internal class UtbetalingKvitteringConsumerTest {

    @Test
    fun `should throw when unknown utbetalingId`() {
        val utbetalingId = UUID30.randomUUID()
        val serviceMock = mock<UtbetalingService> {
            on { oppdaterMedKvittering(any(), any()) } doReturn FantIkkeUtbetaling.left()
        }
        val consumer = UtbetalingKvitteringConsumer(serviceMock)

        shouldThrow<RuntimeException> {
            consumer.onMessage(kvitteringXml(utbetalingId.toString()))
        }.also {
            it.message shouldBe "Kunne ikke lagre kvittering. Fant ikke utbetaling med id $utbetalingId"
        }
        verify(serviceMock, Times(1)).oppdaterMedKvittering(any(), any())
    }

    @Test
    fun `should add kvittering`() {
        val utbetaling = Utbetaling(
            utbetalingslinjer = emptyList(),
            fnr = FnrGenerator.random()
        )
        val xmlMessage = kvitteringXml(utbetaling.id.toString())
        val clock = Clock.fixed(Tidspunkt.EPOCH.instant, ZoneOffset.UTC)

        val kvittering = Kvittering(
            utbetalingsstatus = Kvittering.Utbetalingsstatus.FEIL,
            originalKvittering = xmlMessage,
            mottattTidspunkt = now(clock)
        )

        val postUpdate = utbetaling.copy(
            kvittering = kvittering
        )

        val serviceMock = mock<UtbetalingService> {
            on { oppdaterMedKvittering(utbetaling.id, kvittering) } doReturn postUpdate.right()
        }

        val consumer = UtbetalingKvitteringConsumer(serviceMock, clock)

        consumer.onMessage(xmlMessage)

        verify(serviceMock, Times(1)).oppdaterMedKvittering(utbetaling.id, kvittering)
    }
}
