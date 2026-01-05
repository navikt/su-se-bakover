package no.nav.su.se.bakover.web.services.statistikk

import no.nav.su.se.bakover.common.infrastructure.config.isGCP
import no.nav.su.se.bakover.common.infrastructure.job.RunCheckFactory
import no.nav.su.se.bakover.common.infrastructure.job.StoppableJob
import no.nav.su.se.bakover.common.infrastructure.job.startStoppableJob
import no.nav.su.se.bakover.service.statistikk.SøknadStatistikkService
import org.slf4j.LoggerFactory
import java.time.Duration

class SøknadStatistikk(
    private val stoppableJob: StoppableJob,
) : StoppableJob by stoppableJob {
    companion object {
        fun startJob(
            runCheckFactory: RunCheckFactory,
            initialDelay: Duration,
            periode: Duration,
            søknadStatistikkService: SøknadStatistikkService,
        ): SøknadStatistikk {
            val log = LoggerFactory.getLogger(SøknadStatistikk::class.java)
            val jobName = SøknadStatistikk::class.simpleName!!
            return startStoppableJob(
                jobName = jobName,
                initialDelay = initialDelay,
                intervall = periode,
                log = log,
                runJobCheck = listOf(runCheckFactory.leaderPod()),
            ) {
                if (isGCP()) {
                    log.info("Kjører $jobName")
                    søknadStatistikkService.hentogSendSøknadStatistikkTilBigquery()
                    log.info("Jobb $jobName er fullført")
                }
            }.let { SøknadStatistikk(it) }
        }
    }
}
