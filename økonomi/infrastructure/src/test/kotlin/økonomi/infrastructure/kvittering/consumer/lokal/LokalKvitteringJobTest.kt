package økonomi.infrastructure.kvittering.consumer.lokal

import arrow.core.nonEmptyListOf
import arrow.core.right
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.Rekkefølge
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.extensions.januar
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.januar
import no.nav.su.se.bakover.domain.grunnlag.Uføregrad
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingslinje
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingsrequest
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemmingsnøkkel
import no.nav.su.se.bakover.domain.oppdrag.utbetaling.UtbetalingRepo
import no.nav.su.se.bakover.domain.søknadsbehandling.IverksattSøknadsbehandling
import no.nav.su.se.bakover.service.utbetaling.UtbetalingService
import no.nav.su.se.bakover.service.vedtak.FerdigstillVedtakService
import no.nav.su.se.bakover.test.argThat
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.generer
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import økonomi.domain.kvittering.Kvittering
import økonomi.domain.simulering.Simulering
import økonomi.domain.simulering.SimulertMåned
import økonomi.infrastructure.kvittering.consumer.UtbetalingKvitteringConsumer
import java.time.LocalDate
import java.util.UUID

internal class LokalKvitteringJobTest {

    private val tidspunkt = fixedTidspunkt
    private val fnr = Fnr.generer()

    private val utbetaling = Utbetaling.UtbetalingForSimulering(
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
                rekkefølge = Rekkefølge.start(),
            ),
        ),
        behandler = NavIdentBruker.Attestant("attestant"),
        avstemmingsnøkkel = Avstemmingsnøkkel(Tidspunkt.EPOCH),
        sakstype = Sakstype.UFØRE,
    ).toSimulertUtbetaling(
        simulering = Simulering(
            gjelderId = fnr,
            gjelderNavn = "ubrukt",
            datoBeregnet = LocalDate.now(fixedClock),
            nettoBeløp = 0,
            måneder = listOf(SimulertMåned(måned = januar(2021))),
            rawResponse = "LokalKvitterinJobTest baserer ikke denne på rå XML.",
        ),
    ).toOversendtUtbetaling(
        oppdragsmelding = Utbetalingsrequest(value = ""),
    )

    private val kvittering = Kvittering(
        utbetalingsstatus = Kvittering.Utbetalingsstatus.OK,
        originalKvittering = "unused",
        mottattTidspunkt = tidspunkt,
    )

    @Test
    fun `lokalt kvittering jobb persisterer og ferdigstiller innvilgelse`() {
        val utbetalingRepoMock = mock<UtbetalingRepo> {
            on { hentUkvitterteUtbetalinger() } doReturn listOf(utbetaling)
        }
        val utbetalingMedKvittering = utbetaling.toKvittertUtbetaling(
            kvittering = kvittering,
        )
        val utbetalingServiceMock = mock<UtbetalingService> {
            on { oppdaterMedKvittering(any(), any(), anyOrNull()) } doReturn utbetalingMedKvittering.right()
        }
        val innvilgetSøknadsbehandling = mock<IverksattSøknadsbehandling.Innvilget> {}
        val ferdigstillVedtakServiceMock = mock<FerdigstillVedtakService> {
            on { ferdigstillVedtakEtterUtbetaling(any()) } doReturn Unit.right()
        }

        val utbetalingKvitteringConsumer = UtbetalingKvitteringConsumer(
            utbetalingService = utbetalingServiceMock,
            ferdigstillVedtakService = ferdigstillVedtakServiceMock,
            clock = fixedClock,
        )
        LokalKvitteringService(utbetalingRepoMock, utbetalingKvitteringConsumer).run()
        verify(utbetalingRepoMock).hentUkvitterteUtbetalinger()
        verify(utbetalingServiceMock).oppdaterMedKvittering(
            utbetalingId = argThat { it shouldBe utbetaling.id },
            kvittering = argThat { it shouldBe kvittering.copy(originalKvittering = it.originalKvittering) },
            sessionContext = anyOrNull(),
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
