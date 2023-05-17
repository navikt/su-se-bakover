package no.nav.su.se.bakover.web.services.avstemming

import arrow.core.Either
import no.nav.su.se.bakover.common.infrastructure.correlation.withCorrelationId
import no.nav.su.se.bakover.common.infrastructure.jobs.RunCheckFactory
import no.nav.su.se.bakover.common.infrastructure.jobs.shouldRun
import no.nav.su.se.bakover.domain.oppdrag.Fagområde
import no.nav.su.se.bakover.service.avstemming.AvstemmingService
import org.jetbrains.kotlin.utils.addToStdlib.ifTrue
import org.slf4j.LoggerFactory
import java.time.Duration
import java.util.Date
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
                        withCorrelationId {
                            Fagområde.values().forEach { fagområde ->
                                when (fagområde) {
                                    Fagområde.SUALDER -> {
                                        // TODO("simulering_utbetaling_alder legg til ALDER for grensesnittsavstemming")
                                    }
                                    Fagområde.SUUFORE -> {
                                        log.info("Executing $jobName")
                                        avstemmingService.grensesnittsavstemming(fagområde).fold(
                                            { log.error("$jobName failed with error: $it") },
                                            { log.info("$jobName completed successfully. Details: id:${it.id}, fraOgMed:${it.fraOgMed}, tilOgMed:${it.tilOgMed}, amount:{${it.utbetalinger.size}}") },
                                        )
                                    }
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
