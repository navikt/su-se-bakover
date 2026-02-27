package no.nav.su.se.bakover.web.services.aap

import no.nav.su.se.bakover.common.infrastructure.config.isDev
import no.nav.su.se.bakover.common.infrastructure.job.RunCheckFactory
import no.nav.su.se.bakover.common.infrastructure.job.StoppableJob
import no.nav.su.se.bakover.common.infrastructure.job.startStoppableJob
import org.slf4j.LoggerFactory
import java.time.Duration

internal class Aapjobb(
    private val stoppableJob: StoppableJob,
) : StoppableJob by stoppableJob {
    companion object {
        fun startJob(
            initialDelay: Duration,
            periode: Duration,
            aapJobService: AapJobService,
            runJobCheck: RunCheckFactory,
        ): Aapjobb {
            val log = LoggerFactory.getLogger(Aapjobb::class.java)
            val jobName = Aapjobb::class.simpleName!!
            return startStoppableJob(
                jobName = jobName,
                log = log,
                initialDelay = initialDelay,
                intervall = periode,
                runJobCheck = listOf(runJobCheck.manTilFredag0600til2100()),
            ) {
                log.info("Kan kjøre aapjobb {}", isDev())
                if (isDev()) {
                    log.info("Kjører {}", jobName)
                    aapJobService.hentMaksimum()
                } else {
                    log.info("Kjører ikke {}", jobName)
                }
                log.info("Jobb {} er fullført", jobName)
            }.let { Aapjobb(it) }
        }
    }
}
