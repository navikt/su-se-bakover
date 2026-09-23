package økonomi.infrastructure.kvittering.job

import io.kotest.matchers.types.shouldBeInstanceOf
import no.nav.su.se.bakover.common.domain.job.JobbResultat
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.test.fixedClock
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.slf4j.Logger
import økonomi.domain.utbetaling.Utbetaling
import økonomi.domain.utbetaling.UtbetalingRepo
import java.time.Duration

internal class KontrollerUkvitterteUtbetalingerJobTest {
    @Test
    fun `varsler ikke før utbetalingen har ventet i to timer`() {
        val repo = mock<UtbetalingRepo> {
            on { hentUkvitterteUtbetalinger() } doReturn listOf(
                utbetalingOpprettetFor(Duration.ofMinutes(119)),
            )
        }
        val log = mock<Logger>()

        KontrollerUkvitterteUtbetalingerJob.run(
            utbetalingRepo = repo,
            clock = fixedClock,
            maksVentetid = Duration.ofHours(2),
            log = log,
        ).shouldBeInstanceOf<JobbResultat.Ok>()

        verify(log, never()).error(any<String>())
    }

    @Test
    fun `varsler når utbetalingen har ventet i to timer`() {
        val repo = mock<UtbetalingRepo> {
            on { hentUkvitterteUtbetalinger() } doReturn listOf(
                utbetalingOpprettetFor(Duration.ofHours(2)),
            )
        }
        val log = mock<Logger>()

        KontrollerUkvitterteUtbetalingerJob.run(
            utbetalingRepo = repo,
            clock = fixedClock,
            maksVentetid = Duration.ofHours(2),
            log = log,
        ).shouldBeInstanceOf<JobbResultat.DelvisFeilet>()

        verify(log).error(any<String>())
    }

    private fun utbetalingOpprettetFor(ventetid: Duration): Utbetaling.OversendtUtbetaling.UtenKvittering =
        mock {
            on { opprettet } doReturn Tidspunkt.create(fixedClock.instant().minus(ventetid))
        }
}
