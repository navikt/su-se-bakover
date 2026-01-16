package no.nav.su.se.bakover.web.services.statistikk

import no.nav.su.se.bakover.common.infrastructure.job.RunCheckFactory
import no.nav.su.se.bakover.common.infrastructure.job.StoppableJob
import no.nav.su.se.bakover.common.infrastructure.job.startStoppableJob
import no.nav.su.se.bakover.service.statistikk.FritekstAvslagService
import org.slf4j.LoggerFactory
import java.time.Duration

internal class FritekstAvslagJobb(
    private val stoppableJob: StoppableJob,
) : StoppableJob by stoppableJob {
    companion object {
        fun startJob(
            runCheckFactory: RunCheckFactory,
            fritekstAvslagService: FritekstAvslagService,
            initialDelay: Duration,
            periode: Duration,
        ): FritekstAvslagJobb {
            val log = LoggerFactory.getLogger(FritekstAvslagJobb::class.java)
            val jobName = FritekstAvslagJobb::class.simpleName!!
            return startStoppableJob(
                jobName = jobName,
                initialDelay = initialDelay,
                intervall = periode,
                log = log,
                runJobCheck = listOf(runCheckFactory.leaderPod()),
            ) {
                log.info("Kjører $jobName")
                fritekstAvslagService.hentOgSendAvslagFritekstTilBigquery()
                log.info("Jobb $jobName er fullført")
            }.let { FritekstAvslagJobb(it) }
        }
    }
}
