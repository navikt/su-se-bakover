package no.nav.su.se.bakover.web.services.statistikk

import no.nav.su.se.bakover.common.infrastructure.config.isGCP
import no.nav.su.se.bakover.common.infrastructure.job.RunCheckFactory
import no.nav.su.se.bakover.common.infrastructure.job.StoppableJob
import no.nav.su.se.bakover.common.infrastructure.job.startStoppableJob
import no.nav.su.se.bakover.service.statistikk.StønadStatistikkJobService
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.Duration
import java.util.Date

class StønadStatistikkTilBigQuery(
    private val stoppableJob: StoppableJob,
) : StoppableJob by stoppableJob {
    companion object {

        fun startJob(
            starttidspunkt: Date,
            runCheckFactory: RunCheckFactory,
            periode: Duration,
            stønadJobService: StønadStatistikkJobService,
            clock: Clock,
        ): StønadStatistikkTilBigQuery {
            val log = LoggerFactory.getLogger(StønadStatistikkTilBigQuery::class.java)
            val jobName = StønadStatistikkTilBigQuery::class.simpleName!!
            return startStoppableJob(
                jobName = jobName,
                startAt = starttidspunkt,
                log = log,
                runJobCheck = listOf(runCheckFactory.leaderPod()),
                intervall = periode,
            ) {
                if (isGCP()) {
                    log.info("Kjører $jobName")
                    stønadJobService.lastTilBigQuery(clock = clock)
                    log.info("Jobb $jobName er fullført")
                }
            }.let { StønadStatistikkTilBigQuery(it) }
        }
    }
}
