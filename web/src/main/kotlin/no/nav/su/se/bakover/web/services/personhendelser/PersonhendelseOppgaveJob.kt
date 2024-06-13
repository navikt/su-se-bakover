package no.nav.su.se.bakover.web.services.personhendelser

import no.nav.su.se.bakover.common.infrastructure.job.RunCheckFactory
import no.nav.su.se.bakover.common.infrastructure.job.StoppableJob
import no.nav.su.se.bakover.common.infrastructure.job.startStoppableJob
import no.nav.su.se.bakover.service.personhendelser.PersonhendelseService
import org.slf4j.LoggerFactory
import java.time.Duration

internal class PersonhendelseOppgaveJob(
    private val stoppableJob: StoppableJob,
) : StoppableJob by stoppableJob {
    companion object {
        fun startJob(
            personhendelseService: PersonhendelseService,
            periode: Duration,
            initialDelay: Duration,
            runCheckFactory: RunCheckFactory,
        ): PersonhendelseOppgaveJob {
            return startStoppableJob(
                jobName = "Opprett personhendelse oppgaver",
                initialDelay = initialDelay,
                intervall = periode,
                log = LoggerFactory.getLogger(PersonhendelseOppgaveJob::class.java),
                runJobCheck = listOf(runCheckFactory.leaderPod()),
            ) { personhendelseService.opprettOppgaverForPersonhendelser() }.let {
                PersonhendelseOppgaveJob(it)
            }
        }
    }
}
