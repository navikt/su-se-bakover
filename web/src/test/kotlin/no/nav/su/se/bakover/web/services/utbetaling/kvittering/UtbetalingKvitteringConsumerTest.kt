package no.nav.su.se.bakover.web.services.utbetaling.kvittering

import arrow.core.left
import arrow.core.nonEmptyListOf
import arrow.core.right
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.idag
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.oppdrag.Kvittering
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingslinje
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingsrequest
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemmingsnøkkel
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.service.utbetaling.FantIkkeUtbetaling
import no.nav.su.se.bakover.service.utbetaling.UtbetalingService
import no.nav.su.se.bakover.service.vedtak.FerdigstillVedtakService
import no.nav.su.se.bakover.web.FnrGenerator
import no.nav.su.se.bakover.web.argThat
import no.nav.su.se.bakover.web.fixedClock
import no.nav.su.se.bakover.web.routes.søknadsbehandling.BehandlingTestUtils.sakId
import no.nav.su.se.bakover.web.routes.søknadsbehandling.BehandlingTestUtils.saksnummer
import no.nav.su.se.bakover.web.services.utbetaling.kvittering.UtbetalingKvitteringResponseTest.Companion.avstemmingsnøkkelIXml
import no.nav.su.se.bakover.web.services.utbetaling.kvittering.UtbetalingKvitteringResponseTest.Companion.kvitteringXml
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.internal.verification.Times
import java.time.Clock
import java.time.ZoneOffset

internal class UtbetalingKvitteringConsumerTest {

    private val avstemmingsnøkkel = Avstemmingsnøkkel.fromString(avstemmingsnøkkelIXml)
    private val clock = Clock.fixed(Tidspunkt.EPOCH.instant, ZoneOffset.UTC)
    private val utbetalingUtenKvittering = Utbetaling.OversendtUtbetaling.UtenKvittering(
        sakId = sakId,
        saksnummer = saksnummer,
        utbetalingslinjer = nonEmptyListOf(
            Utbetalingslinje.Ny(
                id = UUID30.randomUUID(),
                opprettet = Tidspunkt.EPOCH,
                fraOgMed = 1.januar(2021),
                tilOgMed = 31.januar(2021),
                forrigeUtbetalingslinjeId = null,
                beløp = 0
            )
        ),
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
        behandler = NavIdentBruker.Attestant("Z123")
    )

    @Test
    fun `kaster exception ved ukjent utbetalings id`() {
        val serviceMock = mock<UtbetalingService> {
            on { oppdaterMedKvittering(eq(avstemmingsnøkkel), any()) } doReturn FantIkkeUtbetaling.left()
        }
        val consumer = UtbetalingKvitteringConsumer(serviceMock, mock(), fixedClock)

        shouldThrow<RuntimeException> {
            consumer.onMessage(kvitteringXml())
        }.also {
            it.message shouldBe "Kunne ikke lagre kvittering. Fant ikke utbetaling med avstemmingsnøkkel $avstemmingsnøkkel"
        }
        verify(serviceMock, Times(2)).oppdaterMedKvittering(any(), any())
    }

    @Test
    fun `kaster videre eventuelle exceptions fra kall til ferdigstill`() {
        val xmlMessage = kvitteringXml(UtbetalingKvitteringResponse.Alvorlighetsgrad.OK)
        val utbetaling = utbetalingUtenKvittering.copy(type = Utbetaling.UtbetalingsType.NY)

        val kvittering = Kvittering(
            utbetalingsstatus = Kvittering.Utbetalingsstatus.OK,
            originalKvittering = xmlMessage,
            mottattTidspunkt = Tidspunkt.now(clock)
        )
        val utbetalingMedKvittering = utbetaling.toKvittertUtbetaling(kvittering)

        val utbetalingServiceMock = mock<UtbetalingService> {
            on { oppdaterMedKvittering(any(), any()) } doReturn utbetalingMedKvittering.right()
        }

        val ferdigstillVedtakServiceMock = mock<FerdigstillVedtakService> {
            on { ferdigstillVedtakEtterUtbetaling(any()) }.thenThrow(IllegalArgumentException("Kastet fra FerdigstillIverksettingService"))
        }

        val consumer = UtbetalingKvitteringConsumer(utbetalingServiceMock, ferdigstillVedtakServiceMock, clock)

        assertThrows<RuntimeException> {
            consumer.onMessage(xmlMessage)
        }.let {
            it.message shouldContain "Kastet fra FerdigstillIverksettingService"
        }

        verify(utbetalingServiceMock).oppdaterMedKvittering(
            avstemmingsnøkkel = argThat { it shouldBe avstemmingsnøkkel },
            kvittering = argThat { it shouldBe kvittering }
        )

        verify(ferdigstillVedtakServiceMock).ferdigstillVedtakEtterUtbetaling(
            argThat { it shouldBe utbetalingMedKvittering }
        )
    }
}
