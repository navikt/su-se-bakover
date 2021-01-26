package no.nav.su.se.bakover.web.services.utbetaling.kvittering

import arrow.core.right
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.timeout
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyNoMoreInteractions
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.endOfDay
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.database.utbetaling.UtbetalingRepo
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.behandling.Behandling
import no.nav.su.se.bakover.domain.oppdrag.Kvittering
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingsrequest
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemmingsnøkkel
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.service.behandling.BehandlingService
import no.nav.su.se.bakover.service.utbetaling.UtbetalingService
import no.nav.su.se.bakover.web.FnrGenerator
import no.nav.su.se.bakover.web.argThat
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

internal class LokalKvitteringJobTest {

    private val fixedClock: Clock = Clock.fixed(1.januar(2020).endOfDay(ZoneOffset.UTC).instant, ZoneOffset.UTC)
    val tidspunkt = Tidspunkt.now(fixedClock)
    val fnr = FnrGenerator.random()

    private val utbetaling = Utbetaling.OversendtUtbetaling.UtenKvittering(
        id = UUID30.randomUUID(),
        opprettet = tidspunkt,
        sakId = UUID.randomUUID(),
        saksnummer = Saksnummer(1234),
        fnr = fnr,
        utbetalingslinjer = listOf(),
        type = Utbetaling.UtbetalingsType.NY,
        behandler = NavIdentBruker.Attestant("attestant"),
        avstemmingsnøkkel = Avstemmingsnøkkel(Tidspunkt.EPOCH),
        simulering = Simulering(
            gjelderId = fnr,
            gjelderNavn = "ubrukt",
            datoBeregnet = LocalDate.now(fixedClock),
            nettoBeløp = 0,
            periodeList = listOf()
        ),
        utbetalingsrequest = Utbetalingsrequest(value = "")
    )

    private val kvittering = Kvittering(
        utbetalingsstatus = Kvittering.Utbetalingsstatus.OK,
        originalKvittering = "unused",
        mottattTidspunkt = tidspunkt
    )

    @Test
    fun `lokalt kvittering jobb persisterer og ferdigstiller innvilgelse`() {
        val utbetalingRepoMock = mock<UtbetalingRepo> {
            on { hentUkvitterteUtbetalinger() } doReturn listOf(utbetaling)
        }
        val utbetalingServiceMock = mock<UtbetalingService> {
            on { oppdaterMedKvittering(any(), any()) } doReturn utbetaling.toKvittertUtbetaling(
                kvittering = kvittering
            ).right()
        }
        val behandlingMock = mock<Behandling> {
        }
        val behandlingServiceMock = mock<BehandlingService> {
            on { hentBehandlingForUtbetaling(any()) } doReturn behandlingMock.right()
        }
        val utbetalingKvitteringConsumer = UtbetalingKvitteringConsumer(
            utbetalingService = utbetalingServiceMock,
            behandlingService = behandlingServiceMock,
            clock = fixedClock
        )
        LokalKvitteringJob(utbetalingRepoMock, utbetalingKvitteringConsumer).schedule()
        verify(utbetalingRepoMock, timeout(1000)).hentUkvitterteUtbetalinger()
        verify(utbetalingServiceMock, timeout(1000)).oppdaterMedKvittering(
            avstemmingsnøkkel = argThat { it shouldBe utbetaling.avstemmingsnøkkel },
            kvittering = argThat { it shouldBe kvittering.copy(originalKvittering = it.originalKvittering) }
        )
        verify(behandlingServiceMock, timeout(1000)).hentBehandlingForUtbetaling(
            argThat { it shouldBe utbetaling.id }
        )

        verify(behandlingServiceMock, timeout(1000)).ferdigstillInnvilgelse(
            argThat { it shouldBe behandlingMock }
        )

        verifyNoMoreInteractions(utbetalingRepoMock, utbetalingServiceMock, behandlingServiceMock, behandlingMock)
    }
}
