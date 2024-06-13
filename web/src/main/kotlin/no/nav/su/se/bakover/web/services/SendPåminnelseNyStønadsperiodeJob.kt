package no.nav.su.se.bakover.web.services

import no.nav.su.se.bakover.common.infrastructure.job.RunCheckFactory
import no.nav.su.se.bakover.common.infrastructure.job.StoppableJob
import no.nav.su.se.bakover.common.infrastructure.job.startStoppableJob
import no.nav.su.se.bakover.service.SendPåminnelserOmNyStønadsperiodeService
import org.slf4j.LoggerFactory
import java.time.Duration

internal class SendPåminnelseNyStønadsperiodeJob(
    private val stoppableJob: StoppableJob,
) : StoppableJob by stoppableJob {
    companion object {
        fun startJob(
            intervall: Duration,
            initialDelay: Duration,
            sendPåminnelseService: SendPåminnelserOmNyStønadsperiodeService,
            runCheckFactory: RunCheckFactory,
        ): SendPåminnelseNyStønadsperiodeJob {
            return startStoppableJob(
                jobName = "SendPåminnelseNyStønadsperiodeJob",
                initialDelay = initialDelay,
                intervall = intervall,
                log = LoggerFactory.getLogger(SendPåminnelseNyStønadsperiodeJob::class.java),
                runJobCheck = listOf(runCheckFactory.leaderPod()),
            ) {
                sendPåminnelseService.sendPåminnelser()
            }.let {
                SendPåminnelseNyStønadsperiodeJob(it)
            }
        }
    }
}
