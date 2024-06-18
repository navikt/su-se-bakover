package no.nav.su.se.bakover.web.services.dokument

import no.nav.su.se.bakover.common.infrastructure.job.RunCheckFactory
import no.nav.su.se.bakover.common.infrastructure.job.StoppableJob
import no.nav.su.se.bakover.common.infrastructure.job.startStoppableJob
import no.nav.su.se.bakover.service.journalføring.JournalføringService
import org.slf4j.LoggerFactory
import java.time.Duration

/**
 * Jobb som med jevne mellomrom sjekker om det eksisterer dokumenter med behov for journalføring.
 *  Eksempeldokumenter:
 *  - [dokument.domain.Dokumentdistribusjon],
 *  - [vilkår.skatt.domain.Skattedokument]
 */
internal class JournalførDokumentJob(
    private val stoppableJob: StoppableJob,
) : StoppableJob by stoppableJob {
    companion object {
        fun startJob(
            initialDelay: Duration,
            periode: Duration,
            runCheckFactory: RunCheckFactory,
            journalføringService: JournalføringService,
        ): JournalførDokumentJob {
            return startStoppableJob(
                jobName = "Journalfør dokumenter",
                initialDelay = initialDelay,
                intervall = periode,
                log = LoggerFactory.getLogger(JournalførDokumentJob::class.java),
                runJobCheck = listOf(runCheckFactory.leaderPod()),
            ) {
                journalføringService.journalfør()
            }.let { JournalførDokumentJob(it) }
        }
    }
}
