package no.nav.su.se.bakover.web.services.statistikk

import no.nav.su.se.bakover.common.infrastructure.job.RunCheckFactory
import no.nav.su.se.bakover.common.infrastructure.job.StoppableJob
import no.nav.su.se.bakover.common.infrastructure.job.startStoppableJob
import no.nav.su.se.bakover.service.statistikk.StønadStatistikkJobService
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.Duration

internal class StønadstatistikkJob(
    private val stoppableJob: StoppableJob,
) : StoppableJob by stoppableJob {
    companion object {

        fun startJob(
            clock: Clock,
            initialDelay: Duration,
            periode: Duration,
            runCheckFactory: RunCheckFactory,
            stønadStatistikkJobService: StønadStatistikkJobService,
        ): StønadstatistikkJob {
            val log = LoggerFactory.getLogger(StønadstatistikkJob::class.java)
            val jobName = StønadstatistikkJob::class.simpleName!!
            return startStoppableJob(
                jobName = jobName,
                initialDelay = initialDelay,
                intervall = periode,
                log = log,
                runJobCheck = listOf(runCheckFactory.leaderPod()),
            ) {
                log.info("Kjører $jobName")
                stønadStatistikkJobService.lagMånedligStønadstatistikk(clock)
                log.info("Jobb $jobName er fullført")
            }.let { StønadstatistikkJob(it) }
        }
    }
}
