package no.nav.su.se.bakover.institusjonsopphold.presentation

import arrow.core.Either
import no.nav.su.se.bakover.common.infrastructure.correlation.withCorrelationId
import no.nav.su.se.bakover.common.infrastructure.jobs.RunCheckFactory
import no.nav.su.se.bakover.common.infrastructure.jobs.shouldRun
import no.nav.su.se.bakover.institusjonsopphold.application.service.InstitusjonsoppholdService
import org.jetbrains.kotlin.utils.addToStdlib.ifTrue
import org.slf4j.LoggerFactory
import java.time.Duration
import kotlin.concurrent.fixedRateTimer

class InstitusjonsoppholdOppgaveJob(
    private val institusjonsoppholdService: InstitusjonsoppholdService,
    private val periode: Duration,
    private val initialDelay: Duration,
    private val runCheckFactory: RunCheckFactory,
) {
    private val log = LoggerFactory.getLogger(this::class.java)
    private val jobName = "Institusjonsopphold-hendelse oppgave"

    fun schedule() {
        log.info("Starter skeduleringsjobb '$jobName' med initialDelay: $initialDelay og periode: $periode")
        fixedRateTimer(
            name = jobName,
            daemon = true,
            period = periode.toMillis(),
            initialDelay = initialDelay.toMillis(),
        ) {
            log.info("Kjører skeduleringsjobb '$jobName'")
            Either.catch {
                listOf(runCheckFactory.leaderPod())
                    .shouldRun()
                    .ifTrue { withCorrelationId { institusjonsoppholdService.opprettOppgaveForHendelser(jobName) } }
            }.mapLeft { log.error("Skeduleringsjobb '$jobName' feilet med stacktrace:", it) }
        }
    }
}
