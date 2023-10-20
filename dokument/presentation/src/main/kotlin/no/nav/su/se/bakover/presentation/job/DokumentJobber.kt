package no.nav.su.se.bakover.presentation.job

import arrow.core.Either
import no.nav.su.se.bakover.common.infrastructure.correlation.withCorrelationId
import no.nav.su.se.bakover.common.infrastructure.jobs.RunCheckFactory
import no.nav.su.se.bakover.common.infrastructure.jobs.shouldRun
import no.nav.su.se.bakover.dokument.application.consumer.JournalførDokumentHendelserKonsument
import org.jetbrains.kotlin.utils.addToStdlib.ifTrue
import org.slf4j.LoggerFactory
import java.time.Duration
import kotlin.concurrent.fixedRateTimer

class DokumentJobber(
    private val initialDelay: Duration,
    private val intervall: Duration,
    private val runCheckFactory: RunCheckFactory,
    private val journalførtDokumentHendelserKonsument: JournalførDokumentHendelserKonsument,

) {
    private val log = LoggerFactory.getLogger(this::class.java)
    private val jobName = "Dokument"
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
                        journalførtDokumentHendelserKonsument.journalførDokumenter(correlationId)
                    }
                }.mapLeft {
                    log.error("Skeduleringsjobb '$jobName' feilet med stacktrace:", it)
                }
            }
        }
    }
}
