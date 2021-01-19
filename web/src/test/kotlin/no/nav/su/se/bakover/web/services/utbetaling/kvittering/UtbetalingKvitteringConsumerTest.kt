package no.nav.su.se.bakover.web.services.utbetaling.kvittering

import arrow.core.left
import arrow.core.right
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyNoMoreInteractions
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.idag
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.behandling.Behandling
import no.nav.su.se.bakover.domain.oppdrag.Kvittering
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingsrequest
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemmingsnøkkel
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.service.behandling.BehandlingService
import no.nav.su.se.bakover.service.behandling.FantIkkeBehandling
import no.nav.su.se.bakover.service.utbetaling.FantIkkeUtbetaling
import no.nav.su.se.bakover.service.utbetaling.UtbetalingService
import no.nav.su.se.bakover.web.FnrGenerator
import no.nav.su.se.bakover.web.argThat
import no.nav.su.se.bakover.web.routes.behandling.BehandlingTestUtils.nyBehandling
import no.nav.su.se.bakover.web.routes.behandling.BehandlingTestUtils.sakId
import no.nav.su.se.bakover.web.routes.behandling.BehandlingTestUtils.saksnummer
import no.nav.su.se.bakover.web.services.utbetaling.kvittering.UtbetalingKvitteringResponseTest.Companion.avstemmingsnøkkelIXml
import no.nav.su.se.bakover.web.services.utbetaling.kvittering.UtbetalingKvitteringResponseTest.Companion.kvitteringXml
import org.junit.jupiter.api.Test
import org.mockito.internal.verification.Times
import java.time.Clock
import java.time.ZoneOffset

internal class UtbetalingKvitteringConsumerTest {

    private val avstemmingsnøkkel = Avstemmingsnøkkel.fromString(avstemmingsnøkkelIXml)
    private val clock = Clock.fixed(Tidspunkt.EPOCH.instant, ZoneOffset.UTC)
    private val utbetalingUtenKvittering = Utbetaling.OversendtUtbetaling.UtenKvittering(
        sakId = sakId,
        saksnummer = saksnummer,
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
        behandler = NavIdentBruker.Attestant("Z123")
    )

    @Test
    fun `should throw when unknown utbetalingId`() {

        val serviceMock = mock<UtbetalingService> {
            on { oppdaterMedKvittering(eq(avstemmingsnøkkel), any()) } doReturn FantIkkeUtbetaling.left()
        }
        val consumer = UtbetalingKvitteringConsumer(serviceMock, mock())

        shouldThrow<RuntimeException> {
            consumer.onMessage(kvitteringXml())
        }.also {
            it.message shouldBe "Kunne ikke lagre kvittering. Fant ikke utbetaling med avstemmingsnøkkel $avstemmingsnøkkel"
        }
        verify(serviceMock, Times(2)).oppdaterMedKvittering(any(), any())
    }

    @Test
    fun `should add kvittering uten å ferdigstille behandling for stans of gjenoppta`() {
        val xmlMessage = kvitteringXml(UtbetalingKvitteringResponse.Alvorlighetsgrad.ALVORLIG_FEIL)
        listOf(Utbetaling.UtbetalingsType.GJENOPPTA, Utbetaling.UtbetalingsType.STANS).forEach { utbetalingstype ->

            val utbetaling = utbetalingUtenKvittering.copy(type = utbetalingstype)

            val kvittering = Kvittering(
                utbetalingsstatus = Kvittering.Utbetalingsstatus.FEIL,
                originalKvittering = xmlMessage,
                mottattTidspunkt = Tidspunkt.now(clock)
            )

            val utbetalingMedKvittering = utbetaling.toKvittertUtbetaling(kvittering)

            val utbetalingServiceMock = mock<UtbetalingService> {
                on { oppdaterMedKvittering(any(), any()) } doReturn utbetalingMedKvittering.right()
            }

            val behandlingServiceMock = mock<BehandlingService>()
            val consumer = UtbetalingKvitteringConsumer(utbetalingServiceMock, behandlingServiceMock, clock)

            consumer.onMessage(xmlMessage)

            verify(utbetalingServiceMock, Times(1)).oppdaterMedKvittering(
                avstemmingsnøkkel = argThat { it shouldBe avstemmingsnøkkel },
                kvittering = argThat { it shouldBe kvittering }
            )
            verifyNoMoreInteractions(utbetalingServiceMock, behandlingServiceMock)
        }
    }

    @Test
    fun `prøver ikke ferdigstille behandling dersom kvittering er feil`() {
        val xmlMessage = kvitteringXml(UtbetalingKvitteringResponse.Alvorlighetsgrad.ALVORLIG_FEIL)
        val utbetaling = utbetalingUtenKvittering.copy(type = Utbetaling.UtbetalingsType.NY)

        val kvittering = Kvittering(
            utbetalingsstatus = Kvittering.Utbetalingsstatus.FEIL,
            originalKvittering = xmlMessage,
            mottattTidspunkt = Tidspunkt.now(clock)
        )

        val utbetalingMedKvittering = utbetaling.toKvittertUtbetaling(kvittering)

        val utbetalingServiceMock = mock<UtbetalingService> {
            on { oppdaterMedKvittering(any(), any()) } doReturn utbetalingMedKvittering.right()
        }

        val behandlingServiceMock = mock<BehandlingService>()
        val consumer = UtbetalingKvitteringConsumer(utbetalingServiceMock, behandlingServiceMock, clock)

        consumer.onMessage(xmlMessage)

        verify(utbetalingServiceMock, Times(1)).oppdaterMedKvittering(
            avstemmingsnøkkel = argThat { it shouldBe avstemmingsnøkkel },
            kvittering = argThat { it shouldBe kvittering }
        )
        verifyNoMoreInteractions(utbetalingServiceMock, behandlingServiceMock)
    }

    @Test
    fun `kan ikke ferdigstille behandling hvis den ikke finnes`() {
        val xmlMessage = kvitteringXml(UtbetalingKvitteringResponse.Alvorlighetsgrad.OK_MED_VARSEL)
        val utbetaling = utbetalingUtenKvittering.copy(type = Utbetaling.UtbetalingsType.NY)

        val kvittering = Kvittering(
            utbetalingsstatus = Kvittering.Utbetalingsstatus.OK_MED_VARSEL,
            originalKvittering = xmlMessage,
            mottattTidspunkt = Tidspunkt.now(clock)
        )

        val utbetalingMedKvittering = utbetaling.toKvittertUtbetaling(kvittering)

        val utbetalingServiceMock = mock<UtbetalingService> {
            on { oppdaterMedKvittering(any(), any()) } doReturn utbetalingMedKvittering.right()
        }

        val behandlingServiceMock = mock<BehandlingService> {
            on { hentBehandlingForUtbetaling(any()) } doReturn FantIkkeBehandling.left()
        }
        val consumer = UtbetalingKvitteringConsumer(utbetalingServiceMock, behandlingServiceMock, clock)

        consumer.onMessage(xmlMessage)

        verify(utbetalingServiceMock, Times(1)).oppdaterMedKvittering(
            avstemmingsnøkkel = argThat { it shouldBe avstemmingsnøkkel },
            kvittering = argThat { it shouldBe kvittering }
        )
        verify(behandlingServiceMock).hentBehandlingForUtbetaling(
            argThat { it shouldBe utbetaling.id }
        )
        verifyNoMoreInteractions(utbetalingServiceMock, behandlingServiceMock)
    }

    @Test
    fun `ferdigstiller behandling hvis den finnes`() {
        val xmlMessage = kvitteringXml(UtbetalingKvitteringResponse.Alvorlighetsgrad.OK)
        val utbetaling = utbetalingUtenKvittering.copy(type = Utbetaling.UtbetalingsType.NY)

        val kvittering = Kvittering(
            utbetalingsstatus = Kvittering.Utbetalingsstatus.OK,
            originalKvittering = xmlMessage,
            mottattTidspunkt = Tidspunkt.now(clock)
        )
        val behandling = nyBehandling().copy(status = Behandling.BehandlingsStatus.IVERKSATT_INNVILGET)

        val utbetalingMedKvittering = utbetaling.toKvittertUtbetaling(kvittering)

        val utbetalingServiceMock = mock<UtbetalingService> {
            on { oppdaterMedKvittering(any(), any()) } doReturn utbetalingMedKvittering.right()
        }

        val behandlingServiceMock = mock<BehandlingService> {
            on { hentBehandlingForUtbetaling(any()) } doReturn behandling
                .right()
        }
        val consumer = UtbetalingKvitteringConsumer(utbetalingServiceMock, behandlingServiceMock, clock)

        consumer.onMessage(xmlMessage)

        verify(utbetalingServiceMock, Times(1)).oppdaterMedKvittering(
            avstemmingsnøkkel = argThat { it shouldBe avstemmingsnøkkel },
            kvittering = argThat { it shouldBe kvittering }
        )
        verify(behandlingServiceMock).hentBehandlingForUtbetaling(
            argThat { it shouldBe utbetaling.id }
        )

        verify(behandlingServiceMock).ferdigstillInnvilgelse(
            argThat { it shouldBe behandling }
        )
        verifyNoMoreInteractions(utbetalingServiceMock, behandlingServiceMock)
    }
}
