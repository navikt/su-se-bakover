package no.nav.su.se.bakover.web.services.utbetaling.kvittering

import arrow.core.Either
import org.slf4j.LoggerFactory
import kotlin.concurrent.fixedRateTimer

/**
 * Only to be used when running locally.
 * Runs a regular job, looking for utbetalinger without kvittering and `ferdigstiller utbetaling`
 */
internal class LokalKvitteringJob(
    private val lokalKvitteringService: LokalKvitteringService,
) {
    private val log = LoggerFactory.getLogger(this::class.java)

    fun schedule() {
        val delayISekunder = 10L
        log.error("Lokal jobb: Startet skedulert jobb for kvitteringer og ferdigstillelse av innvilgelser som kjører hvert $delayISekunder sekund")
        val jobName = "local-ferdigstill-utbetaling"
        fixedRateTimer(
            name = jobName,
            daemon = true,
            period = 1000L * delayISekunder,
        ) {
            Either.catch {
                lokalKvitteringService.run()
            }.mapLeft {
                log.error("Skeduleringsjobb '$jobName' feilet med stacktrace:", it)
            }
        }
    }
}
