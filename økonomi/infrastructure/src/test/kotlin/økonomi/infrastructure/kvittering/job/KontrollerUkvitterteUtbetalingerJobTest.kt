package økonomi.infrastructure.kvittering.job

import io.kotest.matchers.types.shouldBeInstanceOf
import no.nav.su.se.bakover.common.domain.job.JobbResultat
import no.nav.su.se.bakover.common.tid.Tidspunkt
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.slf4j.Logger
import økonomi.domain.utbetaling.Utbetaling
import økonomi.domain.utbetaling.UtbetalingRepo
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneOffset

internal class KontrollerUkvitterteUtbetalingerJobTest {
    private val åpningstid = LocalTime.of(6, 0) to LocalTime.of(21, 0)

    @Test
    fun `varsler ikke før utbetalingen har ventet i to timer`() {
        val clock = Clock.fixed(Instant.parse("2026-09-23T08:00:00Z"), ZoneOffset.UTC)
        val utbetaling = utbetalingOpprettet(Instant.parse("2026-09-23T06:01:00Z"))
        val repo = mock<UtbetalingRepo> {
            on { hentUkvitterteUtbetalinger() } doReturn listOf(utbetaling)
        }
        val log = mock<Logger>()

        KontrollerUkvitterteUtbetalingerJob.run(
            utbetalingRepo = repo,
            clock = clock,
            maksVentetid = Duration.ofHours(2),
            ordinærÅpningstidOppdrag = åpningstid,
            log = log,
        ).shouldBeInstanceOf<JobbResultat.Ok>()

        verify(log, never()).error(any<String>())
    }

    @Test
    fun `varsler når utbetalingen har ventet i to timer`() {
        val clock = Clock.fixed(Instant.parse("2026-09-23T08:00:00Z"), ZoneOffset.UTC)
        val utbetaling = utbetalingOpprettet(Instant.parse("2026-09-23T06:00:00Z"))
        val repo = mock<UtbetalingRepo> {
            on { hentUkvitterteUtbetalinger() } doReturn listOf(utbetaling)
        }
        val log = mock<Logger>()

        KontrollerUkvitterteUtbetalingerJob.run(
            utbetalingRepo = repo,
            clock = clock,
            maksVentetid = Duration.ofHours(2),
            ordinærÅpningstidOppdrag = åpningstid,
            log = log,
        ).shouldBeInstanceOf<JobbResultat.DelvisFeilet>()

        verify(log).error(any<String>())
    }

    @Test
    fun `teller bare ventetid innenfor åpningstiden`() {
        val clock = Clock.fixed(Instant.parse("2026-09-28T05:00:00Z"), ZoneOffset.UTC)
        val utbetaling = utbetalingOpprettet(Instant.parse("2026-09-25T18:30:00Z"))
        val repo = mock<UtbetalingRepo> {
            on { hentUkvitterteUtbetalinger() } doReturn listOf(utbetaling)
        }
        val log = mock<Logger>()

        KontrollerUkvitterteUtbetalingerJob.run(
            utbetalingRepo = repo,
            clock = clock,
            maksVentetid = Duration.ofHours(2),
            ordinærÅpningstidOppdrag = åpningstid,
            log = log,
        ).shouldBeInstanceOf<JobbResultat.Ok>()

        verify(log, never()).error(any<String>())
    }

    @Test
    fun `varsler når samlet ventetid i åpningstiden er to timer`() {
        val clock = Clock.fixed(Instant.parse("2026-09-28T05:30:00Z"), ZoneOffset.UTC)
        val utbetaling = utbetalingOpprettet(Instant.parse("2026-09-25T18:30:00Z"))
        val repo = mock<UtbetalingRepo> {
            on { hentUkvitterteUtbetalinger() } doReturn listOf(utbetaling)
        }
        val log = mock<Logger>()

        KontrollerUkvitterteUtbetalingerJob.run(
            utbetalingRepo = repo,
            clock = clock,
            maksVentetid = Duration.ofHours(2),
            ordinærÅpningstidOppdrag = åpningstid,
            log = log,
        ).shouldBeInstanceOf<JobbResultat.DelvisFeilet>()

        verify(log).error(any<String>())
    }

    private fun utbetalingOpprettet(opprettet: Instant): Utbetaling.OversendtUtbetaling.UtenKvittering =
        mock {
            on { this.opprettet } doReturn Tidspunkt.create(opprettet)
        }
}
