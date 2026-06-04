package no.nav.su.se.bakover.web.services.regulering

import no.nav.su.se.bakover.common.infrastructure.job.RunCheckFactory
import no.nav.su.se.bakover.common.infrastructure.job.StoppableJob
import no.nav.su.se.bakover.common.infrastructure.job.startStoppableJob
import no.nav.su.se.bakover.domain.regulering.ReguleringRetryService
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.Month

internal class RetryIverksettReguleringJob(
    private val stoppableJob: StoppableJob,
) : StoppableJob by stoppableJob {
    companion object {
        fun startJob(
            initialDelay: Duration,
            periode: Duration,
            reguleringRetryService: ReguleringRetryService,
            runCheckFactory: RunCheckFactory,
        ): RetryIverksettReguleringJob {
            val log = LoggerFactory.getLogger(RetryIverksettReguleringJob::class.java)
            val jobName = RetryIverksettReguleringJob::class.simpleName!!
            return startStoppableJob(
                jobName = jobName,
                log = log,
                initialDelay = initialDelay,
                intervall = periode,
                runJobCheck = listOf(
                    runCheckFactory.leaderPod(),
                    runCheckFactory.kunIMåneder(Month.JUNE),
                ),
            ) {
                log.info("Kjører $jobName")
                reguleringRetryService.retrySendUtbetalingForIkkeOversendte()
                log.info("Jobb $jobName er fullført")
            }.let { RetryIverksettReguleringJob(it) }
        }
    }
}
