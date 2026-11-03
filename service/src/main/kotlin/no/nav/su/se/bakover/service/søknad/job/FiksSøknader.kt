package no.nav.su.se.bakover.service.søknad.job

import no.nav.su.se.bakover.common.infrastructure.job.RunCheckFactory
import no.nav.su.se.bakover.common.infrastructure.job.StoppableJob
import no.nav.su.se.bakover.common.infrastructure.job.startStoppableJob
import no.nav.su.se.bakover.service.søknad.SøknadService
import org.slf4j.LoggerFactory
import java.time.Duration

// Opprettet manglende oppgaver og journalføringer samt søknadsbehandling
class FiksSøknader(
    private val stoppableJob: StoppableJob,
) : StoppableJob by stoppableJob {
    companion object {
        fun startJob(
            intervall: Duration,
            initialDelay: Duration,
            søknadService: SøknadService,
            runCheckFactory: RunCheckFactory,
        ): FiksSøknader {
            val logger = LoggerFactory.getLogger(FiksSøknader::class.java)
            return startStoppableJob(
                jobName = "FiksSøknaderUtenOppgave",
                initialDelay = initialDelay,
                intervall = intervall,
                log = logger,
                runJobCheck = listOf(runCheckFactory.leaderPod(), runCheckFactory.manTilFredag0600til2100()),
            ) {
                logger.info("Fikser søknader uten journalpost, oppgave eller behandling")
                // TODO: Bevar søknadens opprinnelige mottaks- og registreringstid ved reparasjon.
                // Avklar tidsgrunnlaget for omgjøring før felles mapping i SakStatistikk endres.
                søknadService.opprettManglendeJournalpostOgOppgaveOgSøknadsbehandling()
                logger.info("Ferdig med jobb: 'Fikser søknader uten journalpost, oppgave eller behandling'")
            }.let {
                FiksSøknader(it)
            }
        }
    }
}
