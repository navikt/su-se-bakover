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
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.idag
import no.nav.su.se.bakover.common.now
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.oppdrag.Kvittering
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingsrequest
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemmingsnøkkel
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
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

    private val avstemmingsnøkkel = Avstemmingsnøkkel.fromString(avstemmingsnøkkelIXml)

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

        val utbetaling = Utbetaling.OversendtUtbetaling.UtenKvittering(
            utbetalingslinjer = emptyList(),
            fnr = FnrGenerator.random(),
            utbetalingsrequest = Utbetalingsrequest(""),
            simulering = Simulering(
                gjelderId = Fnr("12345678910"),
                gjelderNavn = "navn",
                datoBeregnet = idag(),
                nettoBeløp = 0,
                periodeList = listOf()
            ),
            type = Utbetaling.UtbetalingsType.NY,
            oppdragId = UUID30.randomUUID(),
            behandler = NavIdentBruker.Attestant("Z123")
        )
        val xmlMessage = kvitteringXml()
        val clock = Clock.fixed(Tidspunkt.EPOCH.instant, ZoneOffset.UTC)

        val kvittering = Kvittering(
            utbetalingsstatus = Kvittering.Utbetalingsstatus.FEIL,
            originalKvittering = xmlMessage,
            mottattTidspunkt = now(clock)
        )

        val postUpdate = utbetaling.toKvittertUtbetaling(kvittering)

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
