package no.nav.su.se.bakover.web.services.pesys

import no.nav.su.se.bakover.common.infrastructure.config.isGCP
import no.nav.su.se.bakover.common.infrastructure.job.RunCheckFactory
import no.nav.su.se.bakover.common.infrastructure.job.StoppableJob
import no.nav.su.se.bakover.common.infrastructure.job.startStoppableJob
import org.slf4j.LoggerFactory
import java.time.Clock

internal class Pesysjobb(
    private val stoppableJob: StoppableJob,
) : StoppableJob by stoppableJob {
    companion object {
        fun startJob(
            clock: Clock,
            initialDelay: java.time.Duration,
            periode: java.time.Duration,
            runCheckFactory: RunCheckFactory,
            pesysjobb: PesysJobService,
        ): Pesysjobb {
            val log = LoggerFactory.getLogger(Pesysjobb::class.java)
            val jobName = Pesysjobb::class.simpleName!!
            return startStoppableJob(
                jobName = jobName,
                log = log,
                initialDelay = initialDelay,
                intervall = periode,
                runJobCheck = listOf(runCheckFactory.leaderPod()),
            ) {
                log.info("Kjører $jobName")
                if (!isGCP()) {
                    pesysjobb.hentDatafraPesys()
                }
                log.info("Jobb $jobName er fullført")
            }.let { Pesysjobb(it) }
        }
    }
}
