package økonomi.infrastructure.kvittering.consumer

import arrow.core.left
import arrow.core.right
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldStartWith
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.domain.vedtak.KunneIkkeFerdigstilleVedtakMedUtbetaling
import no.nav.su.se.bakover.test.argThat
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.utbetaling.oversendtUtbetalingMedKvittering
import no.nav.su.se.bakover.vedtak.application.FerdigstillVedtakService
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.internal.verification.Times
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import økonomi.application.utbetaling.FantIkkeUtbetaling
import økonomi.application.utbetaling.UtbetalingService
import økonomi.domain.kvittering.Kvittering
import økonomi.infrastructure.kvittering.consumer.UtbetalingKvitteringResponseTest.Companion.kvitteringXml

internal class UtbetalingKvitteringConsumerTest {
    @Test
    fun `kaster exception hvis vi ikke klarer å oppdatere kvittering`() {
        val serviceMock = mock<UtbetalingService> {
            on { oppdaterMedKvittering(any(), any(), anyOrNull()) } doReturn FantIkkeUtbetaling.left()
        }
        val consumer = UtbetalingKvitteringConsumer(
            utbetalingService = serviceMock,
            ferdigstillVedtakService = mock(),
            clock = fixedClock,
        )

        shouldThrow<RuntimeException> {
            consumer.onMessage(kvitteringXml(UUID30.randomUUID()))
        }.also {
            it.message shouldStartWith "Kunne ikke oppdatere kvittering eller vedtak ved prosessering av kvittering"
        }
        verify(serviceMock, Times(2)).oppdaterMedKvittering(any(), any(), anyOrNull())
    }

    @Test
    fun `kaster exception hvis vi ikke klarer å oppdatere vedtak`() {
        val utbetalingMedKvittering = oversendtUtbetalingMedKvittering()

        val utbetalingServiceMock = mock<UtbetalingService> {
            on { oppdaterMedKvittering(any(), any(), anyOrNull()) } doReturn utbetalingMedKvittering.right()
        }
        val ferdigstillVedtakServiceMock = mock<FerdigstillVedtakService> {
            on { ferdigstillVedtakEtterUtbetaling(any()) } doReturn KunneIkkeFerdigstilleVedtakMedUtbetaling.FantIkkeVedtakForUtbetalingId(
                UUID30.randomUUID(),
            ).left()
        }
        val consumer = UtbetalingKvitteringConsumer(
            utbetalingService = utbetalingServiceMock,
            ferdigstillVedtakService = ferdigstillVedtakServiceMock,
            clock = fixedClock,
        )

        shouldThrow<RuntimeException> {
            consumer.onMessage(kvitteringXml(utbetalingMedKvittering.id))
        }.also {
            it.message shouldStartWith "Kunne ikke oppdatere kvittering eller vedtak ved prosessering av kvittering"
        }
        verify(ferdigstillVedtakServiceMock, Times(2)).ferdigstillVedtakEtterUtbetaling(any())
    }

    @Test
    fun `kaster videre eventuelle exceptions fra kall til ferdigstill`() {
        val utbetalingMedKvittering = oversendtUtbetalingMedKvittering()

        val xmlMessage = kvitteringXml(
            utbetalingsId = utbetalingMedKvittering.id,
            alvorlighetsgrad = UtbetalingKvitteringResponse.Alvorlighetsgrad.OK,
        )

        val kvittering = Kvittering(
            utbetalingsstatus = Kvittering.Utbetalingsstatus.OK,
            originalKvittering = xmlMessage,
            mottattTidspunkt = fixedTidspunkt,
        )

        val utbetalingServiceMock = mock<UtbetalingService> {
            on { oppdaterMedKvittering(any(), any(), anyOrNull()) } doReturn utbetalingMedKvittering.right()
        }

        val ferdigstillVedtakServiceMock = mock<FerdigstillVedtakService> {
            on { ferdigstillVedtakEtterUtbetaling(any()) }.thenThrow(IllegalArgumentException("Kastet fra FerdigstillIverksettingService"))
        }

        val consumer = UtbetalingKvitteringConsumer(
            utbetalingService = utbetalingServiceMock,
            ferdigstillVedtakService = ferdigstillVedtakServiceMock,
            clock = fixedClock,
        )

        assertThrows<RuntimeException> {
            consumer.onMessage(xmlMessage)
        }.let {
            it.message shouldContain "Kastet fra FerdigstillIverksettingService"
        }

        verify(utbetalingServiceMock).oppdaterMedKvittering(
            utbetalingId = argThat { it shouldBe utbetalingMedKvittering.id },
            kvittering = argThat { it shouldBe kvittering },
            sessionContext = anyOrNull(),
        )

        verify(ferdigstillVedtakServiceMock).ferdigstillVedtakEtterUtbetaling(
            argThat { it shouldBe utbetalingMedKvittering },
        )
    }
}
