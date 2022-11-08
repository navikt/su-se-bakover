package no.nav.su.se.bakover.web.services.personhendelser

import arrow.core.Either
import no.nav.su.se.bakover.common.CorrelationId.Companion.withCorrelationId
import no.nav.su.se.bakover.common.jobs.infrastructure.RunCheckFactory
import no.nav.su.se.bakover.common.jobs.infrastructure.shouldRun
import no.nav.su.se.bakover.service.personhendelser.PersonhendelseService
import org.jetbrains.kotlin.utils.addToStdlib.ifTrue
import org.slf4j.LoggerFactory
import java.time.Duration
import kotlin.concurrent.fixedRateTimer

internal class PersonhendelseOppgaveJob(
    private val personhendelseService: PersonhendelseService,
    private val periode: Duration,
    private val initialDelay: Duration,
    private val runCheckFactory: RunCheckFactory,
) {
    private val log = LoggerFactory.getLogger(this::class.java)
    private val jobName = "Opprett personhendelse oppgaver"

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
                    .ifTrue {
                        withCorrelationId {
                            personhendelseService.opprettOppgaverForPersonhendelser()
                        }
                    }
            }.mapLeft {
                log.error(
                    "Skeduleringsjobb '$jobName' feilet med stacktrace:",
                    it,
                )
            }
        }
    }
}
