package no.nav.su.se.bakover.web.services

import arrow.core.Either
import no.nav.su.se.bakover.common.CorrelationId.Companion.withCorrelationId
import no.nav.su.se.bakover.common.jobs.infrastructure.RunCheckFactory
import no.nav.su.se.bakover.common.jobs.infrastructure.shouldRun
import no.nav.su.se.bakover.service.SendPåminnelserOmNyStønadsperiodeService
import org.jetbrains.kotlin.utils.addToStdlib.ifTrue
import org.slf4j.LoggerFactory
import java.net.InetAddress
import java.time.Duration
import kotlin.concurrent.fixedRateTimer

internal class SendPåminnelseNyStønadsperiodeJob(
    private val intervall: Duration,
    private val initialDelay: Duration,
    private val sendPåminnelseService: SendPåminnelserOmNyStønadsperiodeService,
    private val runCheckFactory: RunCheckFactory,
) {
    private val log = LoggerFactory.getLogger(this::class.java)

    private val jobName = "SendPåminnelseNyStønadsperiodeJob"

    private val toggle = "supstonad.ufore.automatisk.paaminnelse.ny.stonadsperiode"

    fun schedule() {
        log.info("Starter skeduleringsjobb '$jobName' med periode: $intervall. Mitt hostnavn er $hostName.")

        fixedRateTimer(
            name = jobName,
            daemon = true,
            period = intervall.toMillis(),
            initialDelay = initialDelay.toMillis(),
        ) {
            Either.catch {
                withCorrelationId {
                    listOf(
                        runCheckFactory.leaderPod(),
                        runCheckFactory.unleashToggle(toggle),
                    ).shouldRun().ifTrue {
                        sendPåminnelseService.sendPåminnelser()
                    }
                }
            }.mapLeft {
                log.error("Skeduleringsjobb '$jobName' feilet med stacktrace:", it)
            }
        }
    }

    private val hostName = InetAddress.getLocalHost().hostName
}
