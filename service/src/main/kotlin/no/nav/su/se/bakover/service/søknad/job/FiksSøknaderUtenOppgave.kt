package no.nav.su.se.bakover.service.søknad.job

import no.nav.su.se.bakover.common.domain.job.JobbResultat
import no.nav.su.se.bakover.common.infrastructure.job.RunCheckFactory
import no.nav.su.se.bakover.common.infrastructure.job.StoppableJob
import no.nav.su.se.bakover.common.infrastructure.job.startStoppableJobMedResultat
import no.nav.su.se.bakover.service.søknad.SøknadService
import org.slf4j.Logger
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
            return startStoppableJobMedResultat(
                jobName = "FiksSøknaderUtenOppgave",
                initialDelay = initialDelay,
                intervall = intervall,
                log = logger,
                runJobCheck = listOf(runCheckFactory.leaderPod(), runCheckFactory.manTilFredag0600til2100()),
            ) {
                run(søknadService, logger)
            }.let {
                FiksSøknaderUtenOppgave(it)
            }
        }

        internal fun run(søknadService: SøknadService, logger: Logger): JobbResultat {
            logger.info("Fikser søknader uten oppgave")
            val resultat = søknadService.opprettManglendeJournalpostOgOppgave()
            val antallJournalpostfeil = resultat.journalpostResultat.count { it.isLeft() }
            val antallOppgavefeil = resultat.oppgaveResultat.count { it.isLeft() }
            if (antallJournalpostfeil > 0 || antallOppgavefeil > 0) {
                return JobbResultat.DelvisFeilet(
                    "Reparasjon av søknader feilet: $antallJournalpostfeil journalpostfeil og $antallOppgavefeil oppgavefeil",
                )
            }
            logger.info("Ferdig med jobb: 'Fikser søknader uten oppgave'")
            return JobbResultat.Ok
        }
    }
}
