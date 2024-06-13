package økonomi.infrastructure.kvittering.job

import no.nav.su.se.bakover.common.infrastructure.job.RunCheckFactory
import no.nav.su.se.bakover.common.infrastructure.job.StoppableJob
import no.nav.su.se.bakover.common.infrastructure.job.startStoppableJob
import org.slf4j.LoggerFactory
import økonomi.application.kvittering.FerdigstillVedtakEtterMottattKvitteringKonsument
import økonomi.application.kvittering.KnyttKvitteringTilSakOgUtbetalingKonsument
import java.time.Duration

/** Samlejobb for hendelser som angår utbetalingskvitteringer. */
class UtbetalingskvitteringshendelseJob private constructor(
    private val stoppableJob: StoppableJob,
) : StoppableJob by stoppableJob {
    companion object {
        fun startJob(
            knyttKvitteringTilSakOgUtbetalingService: KnyttKvitteringTilSakOgUtbetalingKonsument,
            ferdigstillVedtakEtterMottattKvitteringKonsument: FerdigstillVedtakEtterMottattKvitteringKonsument,
            initialDelay: Duration,
            intervall: Duration,
            runCheckFactory: RunCheckFactory,
        ): UtbetalingskvitteringshendelseJob {
            return startStoppableJob(
                jobName = "KvitteringshendelserJobb",
                initialDelay = initialDelay,
                intervall = intervall,
                log = LoggerFactory.getLogger(UtbetalingskvitteringshendelseJob::class.java),
                runJobCheck = listOf(runCheckFactory.leaderPod()),
            ) { correlationId ->
                knyttKvitteringTilSakOgUtbetalingService.knyttKvitteringerTilSakOgUtbetaling(
                    correlationId = correlationId,
                )
                ferdigstillVedtakEtterMottattKvitteringKonsument.ferdigstillVedtakEtterMottattKvittering()
            }.let { UtbetalingskvitteringshendelseJob(it) }
        }
    }
}
