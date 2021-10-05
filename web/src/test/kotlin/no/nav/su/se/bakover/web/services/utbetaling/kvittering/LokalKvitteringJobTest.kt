package no.nav.su.se.bakover.web.services.utbetaling.kvittering

import arrow.core.nonEmptyListOf
import arrow.core.right
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.endOfDay
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.database.utbetaling.UtbetalingRepo
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.grunnlag.Uføregrad
import no.nav.su.se.bakover.domain.oppdrag.Kvittering
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingslinje
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingsrequest
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemmingsnøkkel
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.service.utbetaling.UtbetalingService
import no.nav.su.se.bakover.service.vedtak.FerdigstillVedtakService
import no.nav.su.se.bakover.test.generer
import no.nav.su.se.bakover.web.argThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

internal class LokalKvitteringJobTest {

    private val fixedClock: Clock = Clock.fixed(1.januar(2020).endOfDay(ZoneOffset.UTC).instant, ZoneOffset.UTC)
    val tidspunkt = Tidspunkt.now(fixedClock)
    val fnr = Fnr.generer()

    private val utbetaling = Utbetaling.OversendtUtbetaling.UtenKvittering(
        id = UUID30.randomUUID(),
        opprettet = tidspunkt,
        sakId = UUID.randomUUID(),
        saksnummer = Saksnummer(2021),
        fnr = fnr,
        utbetalingslinjer = nonEmptyListOf(
            Utbetalingslinje.Ny(
                id = UUID30.randomUUID(),
                opprettet = Tidspunkt.EPOCH,
                fraOgMed = 1.januar(2021),
                tilOgMed = 31.januar(2021),
                forrigeUtbetalingslinjeId = null,
                beløp = 0,
                uføregrad = Uføregrad.parse(50),
            )
        ),
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
        val utbetalingMedKvittering = utbetaling.toKvittertUtbetaling(
            kvittering = kvittering
        )
        val utbetalingServiceMock = mock<UtbetalingService> {

            on { oppdaterMedKvittering(any(), any()) } doReturn utbetalingMedKvittering.right()
        }
        val innvilgetSøknadsbehandling = mock<Søknadsbehandling.Iverksatt.Innvilget> {}
        val ferdigstillVedtakServiceMock = mock<FerdigstillVedtakService>()

        val utbetalingKvitteringConsumer = UtbetalingKvitteringConsumer(
            utbetalingService = utbetalingServiceMock,
            ferdigstillVedtakService = ferdigstillVedtakServiceMock,
            clock = fixedClock,
        )
        LokalKvitteringService(utbetalingRepoMock, utbetalingKvitteringConsumer).run()
        verify(utbetalingRepoMock).hentUkvitterteUtbetalinger()
        verify(utbetalingServiceMock).oppdaterMedKvittering(
            avstemmingsnøkkel = argThat { it shouldBe utbetaling.avstemmingsnøkkel },
            kvittering = argThat { it shouldBe kvittering.copy(originalKvittering = it.originalKvittering) },
        )
        verify(ferdigstillVedtakServiceMock).ferdigstillVedtakEtterUtbetaling(
            argThat { it shouldBe utbetalingMedKvittering },
        )

        verifyNoMoreInteractions(
            utbetalingRepoMock,
            utbetalingServiceMock,
            ferdigstillVedtakServiceMock,
            innvilgetSøknadsbehandling,
        )
    }
}
