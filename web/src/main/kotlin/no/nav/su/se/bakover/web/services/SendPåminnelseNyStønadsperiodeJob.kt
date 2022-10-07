package no.nav.su.se.bakover.web.services

import arrow.core.Either
import no.nav.su.se.bakover.service.SendPåminnelserOmNyStønadsperiodeService
import no.nav.su.se.bakover.service.toggles.ToggleService
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

    fun schedule() {
        log.info("Starter skeduleringsjobb '$jobName' med periode: $intervall. Mitt hostnavn er $hostName.")

        fixedRateTimer(
            name = jobName,
            daemon = true,
            period = intervall.toMillis(),
            initialDelay = initialDelay.toMillis(),
        ) {
            Either.catch {
                listOf(
                    runCheckFactory.leaderPod(),
                    runCheckFactory.unleashToggle(ToggleService.toggleSendAutomatiskPåminnelseOmNyStønadsperiode),
                ).shouldRun().ifTrue {
                    sendPåminnelseService.sendPåminnelser()
                }
            }.mapLeft {
                log.error("Skeduleringsjobb '$jobName' feilet med stacktrace:", it)
            }
        }
    }

    private val hostName = InetAddress.getLocalHost().hostName
}
