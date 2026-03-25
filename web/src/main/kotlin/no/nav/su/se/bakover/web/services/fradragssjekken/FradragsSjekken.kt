package no.nav.su.se.bakover.web.services.fradragssjekken

import no.nav.su.se.bakover.common.infrastructure.config.isDev
import no.nav.su.se.bakover.common.infrastructure.job.RunCheckFactory
import no.nav.su.se.bakover.common.infrastructure.job.StoppableJob
import no.nav.su.se.bakover.common.infrastructure.job.startStoppableJob
import org.slf4j.LoggerFactory
import java.time.Duration

internal class FradragsSjekken(
    private val stoppableJob: StoppableJob,
) : StoppableJob by stoppableJob {
    companion object {
        fun startJob(
            initialDelay: Duration,
            periode: Duration,
            fradragsSjekkenService: FradragsjobbenServiceImpl,
            runJobCheck: RunCheckFactory,
        ): FradragsSjekken {
            val log = LoggerFactory.getLogger(FradragsSjekken::class.java)
            val jobName = FradragsSjekken::class.simpleName!!
            return startStoppableJob(
                jobName = jobName,
                log = log,
                initialDelay = initialDelay,
                intervall = periode,
                runJobCheck = listOf(runJobCheck.manTilFredag0600til2100()),
            ) {
                log.info("Kan kjøre FradragsSjekken {}", isDev())
                if (isDev()) {
                    log.info("Kjører {}", jobName)
                    fradragsSjekkenService.sjekkLøpendeSakerForFradragIEksterneSystemer()
                } else {
                    log.info("Kjører ikke {}", jobName)
                }
                log.info("Jobb {} er fullført", jobName)
            }.let { FradragsSjekken(it) }
        }
    }
}
