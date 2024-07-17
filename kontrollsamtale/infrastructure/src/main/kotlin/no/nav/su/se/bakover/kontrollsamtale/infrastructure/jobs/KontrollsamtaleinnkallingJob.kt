package no.nav.su.se.bakover.kontrollsamtale.infrastructure.jobs

import arrow.core.Either
import no.nav.su.se.bakover.common.infrastructure.job.RunCheckFactory
import no.nav.su.se.bakover.common.infrastructure.job.StoppableJob
import no.nav.su.se.bakover.common.infrastructure.job.startStoppableJob
import no.nav.su.se.bakover.common.sikkerLogg
import no.nav.su.se.bakover.kontrollsamtale.domain.KontrollsamtaleService
import org.slf4j.LoggerFactory
import java.time.Duration
import java.util.Date

/**
 * Jobb som første dag i hver måned sender ut innkallelse til kontrollsamtaler
 */
class KontrollsamtaleinnkallingJob(
    private val stoppableJob: StoppableJob,
) : StoppableJob by stoppableJob {
    companion object {

        fun startJob(
            kontrollsamtaleService: KontrollsamtaleService,
            starttidspunkt: Date,
            periode: Duration,
            runCheckFactory: RunCheckFactory,
        ): KontrollsamtaleinnkallingJob {
            val log = LoggerFactory.getLogger(KontrollsamtaleinnkallingJob::class.java)
            val jobName = "Utsendelse av kontrollsamtaleinnkallelser"
            return startStoppableJob(
                jobName = jobName,
                startAt = starttidspunkt,
                intervall = periode,
                log = log,
                runJobCheck = listOf(runCheckFactory.leaderPod()),
            ) {
                kontrollsamtaleService.hentPlanlagteKontrollsamtaler().map { kontrollsamtale ->
                    // Vi ønsker ikke å la en feil i en enkelt kontrollsamtale hindre resten av jobben i å kjøre.
                    Either.catch {
                        kontrollsamtaleService.kallInn(kontrollsamtale)
                    }.onLeft {
                        log.error(
                            "Job '$jobName' kunne ikke kalle inn til kontrollsamtale. Se sikkerlogg for mer kontekst.",
                            RuntimeException("Trigger stacktrace for enklere debug."),
                        )
                        sikkerLogg.error(
                            "Job '$jobName' kunne ikke kalle inn til kontrollsamtale: $kontrollsamtale",
                            it,
                        )
                    }
                }
            }.let { KontrollsamtaleinnkallingJob(it) }
        }
    }
}
