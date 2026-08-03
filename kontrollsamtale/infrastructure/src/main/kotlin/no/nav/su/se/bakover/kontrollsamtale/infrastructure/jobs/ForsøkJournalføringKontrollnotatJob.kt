package no.nav.su.se.bakover.kontrollsamtale.infrastructure.jobs

import no.nav.su.se.bakover.common.infrastructure.job.RunCheckFactory
import no.nav.su.se.bakover.common.infrastructure.job.StoppableJob
import no.nav.su.se.bakover.common.infrastructure.job.startStoppableJob
import no.nav.su.se.bakover.service.kontrollsamtale.KontrollsamtaleNotatService
import java.time.Duration

class ForsøkJournalføringKontrollnotatJob(
    private val stoppableJob: StoppableJob,
) : StoppableJob by stoppableJob {
    companion object {
        fun startJob(
            intervall: Duration,
            initialDelay: Duration,
            service: KontrollsamtaleNotatService,
            runCheckFactory: RunCheckFactory,
        ): ForsøkJournalføringKontrollnotatJob {
            return startStoppableJob(
                jobName = "ForsøkJournalføringKontrollnotatJob",
                initialDelay = initialDelay,
                intervall = intervall,
                log = org.slf4j.LoggerFactory.getLogger(ForsøkJournalføringKontrollnotatJob::class.java),
                runJobCheck = listOf(runCheckFactory.manTilFredag0600til2100(), runCheckFactory.leaderPod()),
            ) {
                service.forsøkJournalpostPåNytt()
            }.let { ForsøkJournalføringKontrollnotatJob(it) }
        }
    }
}
