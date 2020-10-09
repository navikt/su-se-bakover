package no.nav.su.se.bakover.web.services.utbetaling.kvittering

import arrow.core.left
import arrow.core.right
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.now
import no.nav.su.se.bakover.domain.oppdrag.Kvittering
import no.nav.su.se.bakover.domain.oppdrag.Oppdragsmelding
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemmingsnøkkel
import no.nav.su.se.bakover.service.utbetaling.FantIkkeUtbetaling
import no.nav.su.se.bakover.service.utbetaling.UtbetalingService
import no.nav.su.se.bakover.web.FnrGenerator
import no.nav.su.se.bakover.web.argThat
import no.nav.su.se.bakover.web.services.utbetaling.kvittering.UtbetalingKvitteringResponseTest.Companion.avstemmingsnøkkelIXml
import no.nav.su.se.bakover.web.services.utbetaling.kvittering.UtbetalingKvitteringResponseTest.Companion.kvitteringXml
import org.junit.jupiter.api.Test
import org.mockito.internal.verification.Times
import java.time.Clock
import java.time.ZoneOffset

internal class UtbetalingKvitteringConsumerTest {

    val avstemmingsnøkkel = Avstemmingsnøkkel.fromString(avstemmingsnøkkelIXml)

    @Test
    fun `should throw when unknown utbetalingId`() {

        val serviceMock = mock<UtbetalingService> {
            on { oppdaterMedKvittering(eq(avstemmingsnøkkel), any()) } doReturn FantIkkeUtbetaling.left()
        }
        val consumer = UtbetalingKvitteringConsumer(serviceMock)

        shouldThrow<RuntimeException> {
            consumer.onMessage(kvitteringXml())
        }.also {
            it.message shouldBe "Kunne ikke lagre kvittering. Fant ikke utbetaling med avstemmingsnøkkel $avstemmingsnøkkel"
        }
        verify(serviceMock, Times(1)).oppdaterMedKvittering(any(), any())
    }

    @Test
    fun `should add kvittering`() {

        val utbetaling = Utbetaling.Ny(
            utbetalingslinjer = emptyList(),
            fnr = FnrGenerator.random(),
            oppdragsmelding = Oppdragsmelding(Oppdragsmelding.Oppdragsmeldingstatus.SENDT, "", avstemmingsnøkkel)
        )
        val xmlMessage = kvitteringXml()
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
            on {
                oppdaterMedKvittering(
                    avstemmingsnøkkel = argThat {
                        it shouldBe avstemmingsnøkkel
                    },
                    kvittering = argThat {
                        it shouldBe kvittering
                    }
                )
            } doReturn postUpdate.right()
        }

        val consumer = UtbetalingKvitteringConsumer(serviceMock, clock)

        consumer.onMessage(xmlMessage)

        verify(serviceMock, Times(1)).oppdaterMedKvittering(avstemmingsnøkkel, kvittering)
    }
}
