package no.nav.su.se.bakover.service.søknad.job

import no.nav.su.se.bakover.common.domain.job.JobbResultat
import no.nav.su.se.bakover.common.infrastructure.job.RunCheckFactory
import no.nav.su.se.bakover.common.infrastructure.job.StoppableJob
import no.nav.su.se.bakover.common.infrastructure.job.startStoppableJobMedResultat
import no.nav.su.se.bakover.service.søknad.SøknadService
import org.slf4j.Logger
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
            return startStoppableJobMedResultat(
                jobName = "FiksSøknaderUtenOppgave",
                initialDelay = initialDelay,
                intervall = intervall,
                log = logger,
                runJobCheck = listOf(runCheckFactory.leaderPod(), runCheckFactory.manTilFredag0600til2100()),
            ) {
                run(søknadService, logger)
            }.let {
                FiksSøknader(it)
            }
        }

        internal fun run(søknadService: SøknadService, logger: Logger): JobbResultat {
            logger.info("Fikser søknader uten journalpost, oppgave eller behandling")
            // TODO: Bevar søknadens opprinnelige mottaks- og registreringstid ved reparasjon.
            // Avklar tidsgrunnlaget for omgjøring før felles mapping i SakStatistikk endres.
            val resultat = søknadService.opprettManglendeJournalpostOgOppgaveOgSøknadsbehandling()
            val antallJournalpostfeil = resultat.journalpostResultat.count { it.isLeft() }
            val antallOppgavefeil = resultat.oppgaveResultat.count { it.isLeft() }
            val antallBehandlingsfeil = resultat.behandlingResultat.count { it.isLeft() }
            if (antallJournalpostfeil > 0 || antallOppgavefeil > 0 || antallBehandlingsfeil > 0) {
                return JobbResultat.DelvisFeilet(
                    "Reparasjon av søknader feilet: $antallJournalpostfeil journalpostfeil, " +
                        "$antallOppgavefeil oppgavefeil og $antallBehandlingsfeil behandlingsfeil",
                )
            }
            logger.info("Ferdig med jobb: 'Fikser søknader uten journalpost, oppgave eller behandling'")
            return JobbResultat.Ok
        }
    }
}
