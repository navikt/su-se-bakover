package no.nav.su.se.bakover.service.utbetaling

import no.nav.su.se.bakover.domain.oppdrag.utbetaling.UtbetalingPublisher
import no.nav.su.se.bakover.domain.oppdrag.utbetaling.UtbetalingRepo
import no.nav.su.se.bakover.test.fixedClock
import org.mockito.kotlin.mock
import økonomi.domain.simulering.SimuleringClient
import java.time.Clock

internal class UtbetalingServiceAndMocks(
    val utbetalingRepo: UtbetalingRepo = mock(),
    val simuleringClient: SimuleringClient = mock(),
    val utbetalingPublisher: UtbetalingPublisher = mock(),
    val clock: Clock = fixedClock,
) {
    val service: UtbetalingService = UtbetalingServiceImpl(
        utbetalingRepo = utbetalingRepo,
        simuleringClient = simuleringClient,
        utbetalingPublisher = utbetalingPublisher,
    )

    fun allMocks() = listOf(
        utbetalingRepo,
        simuleringClient,
        utbetalingPublisher,
    ).toTypedArray()

    fun verifyNoMoreInteractions() {
        org.mockito.kotlin.verifyNoMoreInteractions(*allMocks())
    }
}
