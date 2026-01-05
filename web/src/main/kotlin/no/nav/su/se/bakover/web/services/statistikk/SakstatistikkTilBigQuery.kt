package no.nav.su.se.bakover.web.services.statistikk

import no.nav.su.se.bakover.common.infrastructure.config.isGCP
import no.nav.su.se.bakover.common.infrastructure.job.RunCheckFactory
import no.nav.su.se.bakover.common.infrastructure.job.StoppableJob
import no.nav.su.se.bakover.common.infrastructure.job.startStoppableJob
import no.nav.su.se.bakover.service.statistikk.SakStatistikkBigQueryService
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.LocalDate
import java.util.Date

/*
Overfører data til bigquery hver natt for sakstatistikk
 */
internal class SakstatistikkTilBigQuery(
    private val stoppableJob: StoppableJob,
) : StoppableJob by stoppableJob {
    companion object {

        fun startJob(
            starttidspunkt: Date,
            runCheckFactory: RunCheckFactory,
            periode: Duration,
            sakStatistikkBigQueryService: SakStatistikkBigQueryService,
        ): SakstatistikkTilBigQuery {
            val log = LoggerFactory.getLogger(SakstatistikkTilBigQuery::class.java)
            val jobName = SakstatistikkTilBigQuery::class.simpleName!!
            return startStoppableJob(
                jobName = jobName,
                startAt = starttidspunkt,
                log = log,
                runJobCheck = listOf(runCheckFactory.leaderPod()),
                intervall = periode,
            ) {
                if (isGCP()) {
                    log.info("Kjører $jobName")
                    sakStatistikkBigQueryService.lastTilBigQuery(LocalDate.now())
                    log.info("Jobb $jobName er fullført")
                }
            }.let { SakstatistikkTilBigQuery(it) }
        }
    }
}
