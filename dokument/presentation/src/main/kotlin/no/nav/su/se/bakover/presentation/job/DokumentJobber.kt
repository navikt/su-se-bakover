package no.nav.su.se.bakover.presentation.job

import no.nav.su.se.bakover.common.infrastructure.job.RunCheckFactory
import no.nav.su.se.bakover.common.infrastructure.job.StoppableJob
import no.nav.su.se.bakover.common.infrastructure.job.startStoppableJob
import no.nav.su.se.bakover.dokument.application.consumer.DistribuerDokumentHendelserKonsument
import no.nav.su.se.bakover.dokument.application.consumer.JournalførDokumentHendelserKonsument
import org.slf4j.LoggerFactory
import java.time.Duration

class DokumentJobber(
    private val stoppableJob: StoppableJob,
) : StoppableJob by stoppableJob {
    companion object {
        fun startJob(
            initialDelay: Duration,
            intervall: Duration,
            runCheckFactory: RunCheckFactory,
            journalførtDokumentHendelserKonsument: JournalførDokumentHendelserKonsument,
            distribuerDokumentHendelserKonsument: DistribuerDokumentHendelserKonsument,
        ): DokumentJobber {
            return startStoppableJob(
                jobName = "Dokument",
                initialDelay = initialDelay,
                intervall = intervall,
                log = LoggerFactory.getLogger(DokumentJobber::class.java),
                runJobCheck = listOf(runCheckFactory.leaderPod()),
            ) { correlationId ->
                journalførtDokumentHendelserKonsument.journalførDokumenter(correlationId)
                distribuerDokumentHendelserKonsument.distribuer(correlationId)
            }.let { DokumentJobber(it) }
        }
    }
}
