package no.nav.su.se.bakover.web.services.statistikk

import no.nav.su.se.bakover.common.infrastructure.job.RunCheckFactory
import no.nav.su.se.bakover.common.infrastructure.job.StoppableJob
import no.nav.su.se.bakover.common.infrastructure.job.startStoppableJob
import no.nav.su.se.bakover.service.statistikk.StatistikkVisningService
import org.slf4j.LoggerFactory
import java.time.Duration

internal class GenererStatistikkvisningJob(
    private val stoppableJob: StoppableJob,
) : StoppableJob by stoppableJob {
    companion object {
        fun startJob(
            initialDelay: Duration,
            periode: Duration,
            runCheckFactory: RunCheckFactory,
            service: StatistikkVisningService,
        ): GenererStatistikkvisningJob {
            val log = LoggerFactory.getLogger(GenererStatistikkvisningJob::class.java)
            val jobName = GenererStatistikkvisningJob::class.simpleName!!
            return startStoppableJob(
                jobName = jobName,
                initialDelay = initialDelay,
                intervall = periode,
                log = log,
                runJobCheck = listOf(runCheckFactory.leaderPod()),
            ) {
                service.genererVentendeSakstatistikk()
                service.genererVentendeStønadstatistikk()
            }.let(::GenererStatistikkvisningJob)
        }
    }
}
