package no.nav.su.se.bakover.web.services.avstemming

import arrow.core.Either
import no.nav.su.se.bakover.domain.nais.LeaderPodLookup
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Fagområde
import no.nav.su.se.bakover.service.avstemming.AvstemmingService
import no.nav.su.se.bakover.web.services.erLeaderPod
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import java.time.Duration
import java.util.Date
import java.util.UUID
import kotlin.concurrent.fixedRateTimer

internal class GrensesnittsavstemingJob(
    private val avstemmingService: AvstemmingService,
    private val leaderPodLookup: LeaderPodLookup,
    private val starttidspunkt: Date,
    private val periode: Duration,
) {
    private val log = LoggerFactory.getLogger(this::class.java)

    private val jobName = this::class.simpleName!!

    fun schedule() {
        log.info("Scheduling grensesnittsavstemming at time $starttidspunkt, with period $periode")
        fixedRateTimer(
            name = jobName,
            daemon = true,
            startAt = starttidspunkt,
            period = periode.toMillis(),
        ) {
            Grensesnittsavstemming(
                avstemmingService = avstemmingService,
                leaderPodLookup = leaderPodLookup,
                jobName = jobName,
            ).run()
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
                    Fagområde.values().forEach { fagområde ->
                        when (fagområde) {
                            Fagområde.SUALDER -> {
                                // TODO("simulering_utbetaling_alder legg til ALDER for konsistensavstemming")
                            }
                            Fagområde.SUUFORE -> {
                                log.info("Executing $jobName")
                                // Ktor legger på X-Correlation-ID for web-requests, men vi har ikke noe tilsvarende automagi for meldingskøen.
                                MDC.put("X-Correlation-ID", UUID.randomUUID().toString())
                                avstemmingService.grensesnittsavstemming(fagområde).fold(
                                    { log.error("$jobName failed with error: $it") },
                                    { log.info("$jobName completed successfully. Details: id:${it.id}, fraOgMed:${it.fraOgMed}, tilOgMed:${it.tilOgMed}, amount:{${it.utbetalinger.size}}") },
                                )
                            }
                        }
                    }
                }
            }.mapLeft {
                log.error("Skeduleringsjobb '$jobName' feilet med stacktrace:", it)
            }
        }
    }
}
