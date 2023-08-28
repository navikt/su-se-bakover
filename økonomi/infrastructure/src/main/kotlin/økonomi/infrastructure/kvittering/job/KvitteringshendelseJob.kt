package økonomi.infrastructure.kvittering.job

import arrow.core.Either
import no.nav.su.se.bakover.common.infrastructure.correlation.withCorrelationId
import no.nav.su.se.bakover.common.infrastructure.jobs.RunCheckFactory
import no.nav.su.se.bakover.common.infrastructure.jobs.shouldRun
import org.jetbrains.kotlin.utils.addToStdlib.ifTrue
import org.slf4j.LoggerFactory
import økonomi.application.kvittering.FerdigstillVedtakEtterMottattKvitteringKonsument
import økonomi.application.kvittering.KnyttKvitteringTilSakOgUtbetalingKonsument
import java.time.Duration
import kotlin.concurrent.fixedRateTimer

/**
 * Samlejobb for hendelser som angår utbetalingskvitteringer.
 */
class KvitteringshendelseJob(
    private val knyttKvitteringTilSakOgUtbetalingService: KnyttKvitteringTilSakOgUtbetalingKonsument,
    private val ferdigstillVedtakEtterMottattKvitteringKonsument: FerdigstillVedtakEtterMottattKvitteringKonsument,
    private val initialDelay: Duration,
    private val intervall: Duration,
    private val runCheckFactory: RunCheckFactory,
) {
    private val log = LoggerFactory.getLogger(this::class.java)
    private val jobName = "KvitteringshendelserJobb"
    fun schedule() {
        log.info("Starter skeduleringsjobb '$jobName' med intervall: ${intervall.toMinutes()} min")
        fixedRateTimer(
            name = jobName,
            daemon = true,
            initialDelay = initialDelay.toMillis(),
            period = intervall.toMillis(),
        ) {
            listOf(
                runCheckFactory.leaderPod(),
            ).shouldRun().ifTrue {
                Either.catch {
                    withCorrelationId { correlationId ->
                        knyttKvitteringTilSakOgUtbetalingService.knyttKvitteringerTilSakOgUtbetaling(
                            correlationId = correlationId,
                        )
                        ferdigstillVedtakEtterMottattKvitteringKonsument.ferdigstillVedtakEtterMottattKvittering(
                            correlationId = correlationId,
                        )
                    }
                }.mapLeft {
                    log.error("Skeduleringsjobb '$jobName' feilet med stacktrace:", it)
                }
            }
        }
    }
}
