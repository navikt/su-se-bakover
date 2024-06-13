package økonomi.infrastructure.kvittering.job

import no.nav.su.se.bakover.common.infrastructure.job.StoppableJob
import no.nav.su.se.bakover.common.infrastructure.job.startStoppableJob
import org.slf4j.LoggerFactory
import økonomi.infrastructure.kvittering.lokal.LokalKvitteringService
import java.time.Duration

/**
 * Only to be used when running locally.
 * Runs a regular job, looking for utbetalinger without kvittering and `ferdigstiller utbetaling`
 */
class LokalKvitteringJob(
    private val stoppableJob: StoppableJob,
) : StoppableJob by stoppableJob {

    companion object {

        private val log = LoggerFactory.getLogger(LokalKvitteringJob::class.java)
        private const val JOB_NAME = "local-ferdigstill-utbetaling"
        fun startJob(
            lokalKvitteringService: LokalKvitteringService,
            intervall: Duration,
            initialDelay: Duration,
        ): LokalKvitteringJob {
            log.error("Lokal jobb: Starter skedulert jobb '$JOB_NAME' for kvitteringer og ferdigstillelse av innvilgelser. initialDelay: $initialDelay, periode: $intervall")
            return startStoppableJob(
                jobName = JOB_NAME,
                initialDelay = initialDelay,
                intervall = intervall,
                log = log,
                runJobCheck = emptyList(),
            ) {
                lokalKvitteringService.run()
            }.let { LokalKvitteringJob(it) }
        }
    }
}
