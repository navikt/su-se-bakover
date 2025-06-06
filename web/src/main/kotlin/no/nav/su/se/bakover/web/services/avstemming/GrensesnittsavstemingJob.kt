package no.nav.su.se.bakover.web.services.avstemming

import no.nav.su.se.bakover.common.infrastructure.job.RunCheckFactory
import no.nav.su.se.bakover.common.infrastructure.job.StoppableJob
import no.nav.su.se.bakover.common.infrastructure.job.startStoppableJob
import no.nav.su.se.bakover.service.avstemming.AvstemmingService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import økonomi.domain.Fagområde
import java.time.Duration
import java.util.Date

internal class GrensesnittsavstemingJob(
    private val stoppableJob: StoppableJob,
) : StoppableJob by stoppableJob {
    companion object {
        fun startJob(
            avstemmingService: AvstemmingService,
            starttidspunkt: Date,
            periode: Duration,
            runCheckFactory: RunCheckFactory,
        ): GrensesnittsavstemingJob {
            val log = LoggerFactory.getLogger(GrensesnittsavstemingJob::class.java)
            val jobName = GrensesnittsavstemingJob::class.simpleName!!
            return startStoppableJob(
                jobName = jobName,
                startAt = starttidspunkt,
                intervall = periode,
                log = log,
                runJobCheck = listOf(runCheckFactory.leaderPod()),
            ) {
                run(
                    avstemmingService = avstemmingService,
                    log = log,
                    jobName = jobName,
                )
            }.let { GrensesnittsavstemingJob(it) }
        }

        fun run(
            avstemmingService: AvstemmingService,
            log: Logger,
            jobName: String,
        ) {
            Fagområde.entries.forEach { fagområde ->
                avstemmingService.grensesnittsavstemming(fagområde).fold(
                    { log.error("$jobName failed with error: $it") },
                    { log.info("$jobName completed successfully. Details: id:${it.id}, fraOgMed:${it.fraOgMed}, tilOgMed:${it.tilOgMed}, amount:{${it.utbetalinger.size}}") },
                )
            }
        }
    }
}
