package økonomi.infrastructure.kvittering.consumer.lokal

import arrow.core.Either
import no.nav.su.se.bakover.common.infrastructure.correlation.withCorrelationId
import org.slf4j.LoggerFactory
import java.time.Duration
import kotlin.concurrent.fixedRateTimer

/**
 * Only to be used when running locally.
 * Runs a regular job, looking for utbetalinger without kvittering and `ferdigstiller utbetaling`
 */
class LokalKvitteringJob(
    private val lokalKvitteringService: LokalKvitteringService,
    private val periode: Duration,
    private val initialDelay: Duration,
) {
    private val log = LoggerFactory.getLogger(this::class.java)

    fun schedule() {
        log.error("Lokal jobb: Startet skedulert jobb for kvitteringer og ferdigstillelse av innvilgelser. initialDelay: $initialDelay, periode: $periode")
        val jobName = "local-ferdigstill-utbetaling"
        fixedRateTimer(
            name = jobName,
            daemon = true,
            period = periode.toMillis(),
            initialDelay = initialDelay.toMillis(),
        ) {
            Either.catch {
                withCorrelationId {
                    lokalKvitteringService.run()
                }
            }.mapLeft {
                log.error("Skeduleringsjobb '$jobName' feilet med stacktrace:", it)
            }
        }
    }
}
