package no.nav.su.se.bakover.web.services.pesys

import no.nav.su.se.bakover.common.infrastructure.config.isDev
import no.nav.su.se.bakover.common.infrastructure.job.RunCheckFactory
import no.nav.su.se.bakover.common.infrastructure.job.StoppableJob
import no.nav.su.se.bakover.common.infrastructure.job.startStoppableJob
import org.slf4j.LoggerFactory
import java.time.Duration

internal class Pesysjobb(
    private val stoppableJob: StoppableJob,
) : StoppableJob by stoppableJob {
    companion object {
        fun startJob(
            initialDelay: Duration,
            periode: Duration,
            pesysjobb: PesysJobService,
            runJobCheck: RunCheckFactory,
        ): Pesysjobb {
            val log = LoggerFactory.getLogger(Pesysjobb::class.java)
            val jobName = Pesysjobb::class.simpleName!!
            return startStoppableJob(
                jobName = jobName,
                log = log,
                initialDelay = initialDelay,
                intervall = periode,
                runJobCheck = listOf(runJobCheck.manTilFredag0600til2100()),
            ) {
                log.info("Kan kjøre pesysjobb ${isDev()}")
                if (isDev()) {
                    log.info("Kjører $jobName")
                    pesysjobb.hentDataFraAlder()
                    pesysjobb.hentDataFraUføre()
                } else {
                    log.info("Kjører ikke $jobName")
                }
                log.info("Jobb $jobName er fullført")
            }.let { Pesysjobb(it) }
        }
    }
}
