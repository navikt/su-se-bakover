package no.nav.su.se.bakover.service.søknad.job

import no.nav.su.se.bakover.common.infrastructure.job.RunCheckFactory
import no.nav.su.se.bakover.common.infrastructure.job.StoppableJob
import no.nav.su.se.bakover.common.infrastructure.job.startStoppableJob
import no.nav.su.se.bakover.service.søknad.SøknadService
import org.slf4j.LoggerFactory
import java.time.Duration

class FiksSøknaderUtenOppgave(
    private val stoppableJob: StoppableJob,
) : StoppableJob by stoppableJob {
    companion object {
        fun startJob(
            intervall: Duration,
            initialDelay: Duration,
            søknadService: SøknadService,
            runCheckFactory: RunCheckFactory,
        ): FiksSøknaderUtenOppgave {
            val logger = LoggerFactory.getLogger(FiksSøknaderUtenOppgave::class.java)
            return startStoppableJob(
                jobName = "FiksSøknaderUtenOppgave",
                initialDelay = initialDelay,
                intervall = intervall,
                log = logger,
                runJobCheck = listOf(runCheckFactory.leaderPod()),
            ) {
                logger.info("Fikser søknader uten oppgave")
                søknadService.opprettManglendeJournalpostOgOppgave()
                logger.info("Ferdig med jobb: 'Fikser søknader uten oppgave'")
            }.let {
                FiksSøknaderUtenOppgave(it)
            }
        }
    }
}
