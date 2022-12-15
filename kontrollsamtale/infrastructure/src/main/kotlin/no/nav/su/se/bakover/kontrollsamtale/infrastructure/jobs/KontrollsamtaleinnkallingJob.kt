package no.nav.su.se.bakover.kontrollsamtale.infrastructure.jobs

import arrow.core.Either
import no.nav.su.se.bakover.common.CorrelationId
import no.nav.su.se.bakover.common.jobs.infrastructure.RunCheckFactory
import no.nav.su.se.bakover.common.jobs.infrastructure.shouldRun
import no.nav.su.se.bakover.kontrollsamtale.domain.KontrollsamtaleService
import org.jetbrains.kotlin.utils.addToStdlib.ifTrue
import org.slf4j.LoggerFactory
import java.time.Duration
import java.util.Date
import kotlin.concurrent.fixedRateTimer

/**
 * Jobb som første dag i hver måned sender ut innkallelse til kontrollsamtaler
 */
class KontrollsamtaleinnkallingJob(
    private val kontrollsamtaleService: KontrollsamtaleService,
    private val starttidspunkt: Date,
    private val periode: Duration,
    private val runCheckFactory: RunCheckFactory,
) {
    private val log = LoggerFactory.getLogger(this::class.java)

    private val jobName = "Utsendelse av kontrollsamtaleinnkallelser"

    fun schedule() {
        log.info("Starter skeduleringsjobb '$jobName' med periode: $periode og starttidspunkt: $starttidspunkt")
        fixedRateTimer(
            name = jobName,
            daemon = true,
            startAt = starttidspunkt,
            period = periode.toMillis(),
        ) {
            Either.catch {
                log.debug("Kjører skeduleringsjobb '$jobName'")
                listOf(runCheckFactory.leaderPod())
                    .shouldRun()
                    .ifTrue {
                        CorrelationId.withCorrelationId {
                            kontrollsamtaleService.hentPlanlagteKontrollsamtaler().map { kontrollsamtaler ->
                                kontrollsamtaler.forEach {
                                    // TODO jah: Gjør kallInn til parameterløs og gjør denne logikken i den funksjonen.
                                    kontrollsamtaleService.kallInn(it.sakId, it)
                                }
                            }
                        }
                    }
                log.debug("Fullførte skeduleringsjobb '$jobName'")
            }.mapLeft {
                log.error("Skeduleringsjobb '$jobName' feilet med stacktrace:", it)
            }
        }
    }
}
