package no.nav.su.se.bakover.institusjonsopphold.presentation

import no.nav.su.se.bakover.common.infrastructure.job.RunCheckFactory
import no.nav.su.se.bakover.common.infrastructure.job.StoppableJob
import no.nav.su.se.bakover.common.infrastructure.job.startStoppableJob
import no.nav.su.se.bakover.institusjonsopphold.application.service.OpprettOppgaverForInstitusjonsoppholdshendelser
import org.slf4j.LoggerFactory
import java.time.Duration

class InstitusjonsoppholdOppgaveJob(
    private val stoppableJob: StoppableJob,
) : StoppableJob by stoppableJob {
    companion object {
        fun startJob(
            hendelseskonsument: OpprettOppgaverForInstitusjonsoppholdshendelser,
            periode: Duration,
            initialDelay: Duration,
            runCheckFactory: RunCheckFactory,
        ): InstitusjonsoppholdOppgaveJob {
            return startStoppableJob(
                jobName = "Institusjonsopphold-hendelse oppgave",
                initialDelay = initialDelay,
                intervall = periode,
                log = LoggerFactory.getLogger(InstitusjonsoppholdOppgaveJob::class.java),
                runJobCheck = listOf(runCheckFactory.leaderPod()),
            ) { correlationId ->
                hendelseskonsument.opprettOppgaverForHendelser(correlationId)
            }.let { InstitusjonsoppholdOppgaveJob(it) }
        }
    }
}
