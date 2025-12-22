package no.nav.su.se.bakover.web.services.statistikk

import no.nav.su.se.bakover.common.domain.tid.zoneIdOslo
import no.nav.su.se.bakover.common.infrastructure.config.isGCP
import no.nav.su.se.bakover.common.infrastructure.job.RunCheckFactory
import no.nav.su.se.bakover.common.infrastructure.job.StoppableJob
import no.nav.su.se.bakover.common.infrastructure.job.startStoppableJob
import no.nav.su.se.bakover.service.statistikk.StønadStatistikkJobService
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.Duration
import java.time.ZonedDateTime
import java.util.Date

class StønadStatistikkTilBigQuery(
    private val stoppableJob: StoppableJob,
) : StoppableJob by stoppableJob {
    companion object {
        fun runMonthlyJobIfFirstDay(job: () -> Unit) {
            val today = ZonedDateTime.now(zoneIdOslo).toLocalDate()
            if (today.dayOfMonth == 1) {
                job()
            } else {
                // optional: log.skip("Not first of month")
            }
        }

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
                runMonthlyJobIfFirstDay {
                    if (isGCP()) {
                        log.info("Kjører $jobName")
                        stønadJobService.lastTilBigQuery(clock = clock)
                        log.info("Jobb $jobName er fullført")
                    }
                }
            }.let { StønadStatistikkTilBigQuery(it) }
        }
    }
}
