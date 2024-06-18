package no.nav.su.se.bakover.web.services.dokument

import no.nav.su.se.bakover.common.infrastructure.job.RunCheckFactory
import no.nav.su.se.bakover.common.infrastructure.job.StoppableJob
import no.nav.su.se.bakover.common.infrastructure.job.startStoppableJob
import no.nav.su.se.bakover.service.dokument.DistribuerDokumentService
import org.slf4j.LoggerFactory
import java.time.Duration

/**
 * Jobb som med jevne mellomrom sjekker om det eksisterer dokumenter med behov for  bestilling av brev.
 * Se for eksempel [dokument.domain.Dokumentdistribusjon],
 */
internal class DistribuerDokumentJob(
    private val stoppableJob: StoppableJob,
) : StoppableJob by stoppableJob {
    companion object {
        fun startJob(
            initialDelay: Duration,
            periode: Duration,
            runCheckFactory: RunCheckFactory,
            distribueringService: DistribuerDokumentService,
        ): DistribuerDokumentJob {
            startStoppableJob(
                jobName = "Bestill brevdistribusjon",
                initialDelay = initialDelay,
                intervall = periode,
                log = LoggerFactory.getLogger(DistribuerDokumentJob::class.java),
                runJobCheck = listOf(runCheckFactory.leaderPod()),
            ) {
                distribueringService.distribuer()
            }.let {
                return DistribuerDokumentJob(it)
            }
        }
    }
}
