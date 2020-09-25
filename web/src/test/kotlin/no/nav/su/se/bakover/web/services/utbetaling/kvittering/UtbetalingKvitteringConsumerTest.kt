package no.nav.su.se.bakover.web.services.utbetaling.kvittering

import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.MicroInstant
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.now
import no.nav.su.se.bakover.database.ObjectRepo
import no.nav.su.se.bakover.domain.oppdrag.Kvittering
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.web.services.utbetaling.kvittering.UtbetalingKvitteringResponseTest.Companion.kvitteringXml
import org.junit.jupiter.api.Test
import org.mockito.internal.verification.Times
import java.time.Clock
import java.time.ZoneOffset

internal class UtbetalingKvitteringConsumerTest {

    @Test
    fun `should throw when unknown utbetalingId`() {
        val utbetalingId = UUID30.randomUUID()
        val repoMock = mock<ObjectRepo> {
            on { hentUtbetaling(utbetalingId) } doReturn null
        }
        val consumer = UtbetalingKvitteringConsumer(repoMock)

        shouldThrow<RuntimeException> {
            consumer.onMessage(kvitteringXml(utbetalingId.toString()))
        }.also {
            it.message shouldBe "Kunne ikke lagre kvittering. Fant ikke utbetaling med id $utbetalingId"
        }
        verify(repoMock, Times(1)).hentUtbetaling(utbetalingId)
    }

    @Test
    fun `should add kvittering`() {
        val utbetalingId = UUID30.randomUUID()
        val xmlMessage = kvitteringXml(utbetalingId.toString())
        val clock = Clock.fixed(MicroInstant.EPOCH.instant, ZoneOffset.UTC)
        val kvittering = Kvittering(
            utbetalingsstatus = Kvittering.Utbetalingsstatus.FEIL,
            originalKvittering = xmlMessage,
            mottattTidspunkt = now(clock)
        )
        val utbetalingMock = mock<Utbetaling>()

        val repoMock = mock<ObjectRepo> {
            on { hentUtbetaling(utbetalingId) } doReturn utbetalingMock
        }
        val consumer = UtbetalingKvitteringConsumer(repoMock, clock)

        consumer.onMessage(xmlMessage)
        verify(utbetalingMock, Times(1)).addKvittering(kvittering)
        verify(repoMock, Times(1)).hentUtbetaling(utbetalingId)
    }
}
