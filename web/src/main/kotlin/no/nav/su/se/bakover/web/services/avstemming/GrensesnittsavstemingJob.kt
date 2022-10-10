package no.nav.su.se.bakover.web.services.avstemming

import arrow.core.Either
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Fagområde
import no.nav.su.se.bakover.service.avstemming.AvstemmingService
import no.nav.su.se.bakover.web.services.RunCheckFactory
import no.nav.su.se.bakover.web.services.shouldRun
import org.jetbrains.kotlin.utils.addToStdlib.ifTrue
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import java.time.Duration
import java.util.Date
import java.util.UUID
import kotlin.concurrent.fixedRateTimer

internal class GrensesnittsavstemingJob(
    private val avstemmingService: AvstemmingService,
    private val starttidspunkt: Date,
    private val periode: Duration,
    private val runCheckFactory: RunCheckFactory,
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
                jobName = jobName,
                runCheckFactory = runCheckFactory,
            ).run()
        }
    }

    class Grensesnittsavstemming(
        val avstemmingService: AvstemmingService,
        val jobName: String,
        val runCheckFactory: RunCheckFactory,
    ) {
        private val log = LoggerFactory.getLogger(this::class.java)

        fun run() {
            Either.catch {
                listOf(runCheckFactory.leaderPod())
                    .shouldRun()
                    .ifTrue {
                        Fagområde.values().forEach { fagområde ->
                            when (fagområde) {
                                Fagområde.SUALDER -> {
                                    // TODO("simulering_utbetaling_alder legg til ALDER for grensesnittsavstemming")
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
