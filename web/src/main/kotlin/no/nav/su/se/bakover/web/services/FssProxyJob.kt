package no.nav.su.se.bakover.web.services

import no.nav.su.se.bakover.client.proxy.SUProxyClient
import no.nav.su.se.bakover.common.infrastructure.config.isGCP
import no.nav.su.se.bakover.common.infrastructure.job.StoppableJob
import no.nav.su.se.bakover.common.infrastructure.job.startStoppableJob
import java.time.Duration

internal class FssProxyJob(
    private val stoppableJob: StoppableJob,
) : StoppableJob by stoppableJob {
    companion object {
        fun startJob(
            initialDelay: Duration,
            periode: Duration,
            client: SUProxyClient,
        ): FssProxyJob {
            val log = org.slf4j.LoggerFactory.getLogger(FssProxyJob::class.java)
            val jobName = FssProxyJob::class.simpleName!!
            return startStoppableJob(
                jobName = jobName,
                log = log,
                initialDelay = initialDelay,
                intervall = periode,
                runJobCheck = emptyList(),
            ) {
                log.info("Kjører $jobName")
                if (isGCP()) {
                    log.info("Pinger proxy-fss")
                    client.ping()
                    log.info("Pinget proxy-fss")
                }
                log.info("Jobb $jobName er fullført")
            }.let { FssProxyJob(it) }
        }
    }
}
