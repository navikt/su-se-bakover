package no.nav.su.se.bakover.web.services.avstemming

import arrow.core.Either
import no.nav.su.se.bakover.common.zoneIdOslo
import no.nav.su.se.bakover.domain.nais.LeaderPodLookup
import no.nav.su.se.bakover.service.avstemming.AvstemmingService
import no.nav.su.se.bakover.web.services.erLeaderPod
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import java.time.Duration
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.util.Date
import java.util.UUID
import kotlin.concurrent.fixedRateTimer

internal class GrensesnittsavstemingJob(
    private val avstemmingService: AvstemmingService,
    private val leaderPodLookup: LeaderPodLookup,
) {
    private val log = LoggerFactory.getLogger(this::class.java)
    private val jobName = this::class.simpleName!!
    private val periode = Duration.of(1, ChronoUnit.DAYS).toMillis()
    private val avstemmingstidspunkt = ZonedDateTime.now(zoneIdOslo).next(LocalTime.of(1, 0, 0))

    fun schedule() {
        log.info("Scheduling grensesnittsavstemming at time $avstemmingstidspunkt, with interval $periode ms")
        fixedRateTimer(
            name = jobName,
            daemon = true,
            startAt = avstemmingstidspunkt,
            period = periode,
        ) {
            Grensesnittsavstemming(
                avstemmingService = avstemmingService,
                leaderPodLookup = leaderPodLookup,
                jobName = jobName,
            ).run()
        }
    }

    private fun ZonedDateTime.next(atTime: LocalTime): Date {
        return if (this.toLocalTime().isAfter(atTime)) {
            Date.from(
                this.plusDays(1)
                    .withHour(atTime.hour)
                    .withMinute(atTime.minute)
                    .withSecond(atTime.second)
                    .toInstant(),
            )
        } else {
            Date.from(
                this.withHour(atTime.hour)
                    .withMinute(atTime.minute)
                    .withSecond(atTime.second)
                    .toInstant(),
            )
        }
    }

    class Grensesnittsavstemming(
        val avstemmingService: AvstemmingService,
        val leaderPodLookup: LeaderPodLookup,
        val jobName: String,
    ) {
        private val log = LoggerFactory.getLogger(this::class.java)

        fun run() {
            Either.catch {
                if (leaderPodLookup.erLeaderPod()) {
                    log.info("Executing $jobName")
                    // Ktor legger på X-Correlation-ID for web-requests, men vi har ikke noe tilsvarende automagi for meldingskøen.
                    MDC.put("X-Correlation-ID", UUID.randomUUID().toString())
                    avstemmingService.grensesnittsavstemming().fold(
                        { log.error("$jobName failed with error: $it") },
                        { log.info("$jobName completed successfully. Details: id:${it.id}, fraOgMed:${it.fraOgMed}, tilOgMed:${it.tilOgMed}, amount:{${it.utbetalinger.size}}") },
                    )
                }
            }.mapLeft {
                log.error("Skeduleringsjobb '$jobName' feilet med stacktrace:", it)
            }
        }
    }
}
