package no.nav.su.se.bakover.web.services.personhendelser

import arrow.core.Either
import no.nav.su.se.bakover.domain.nais.LeaderPodLookup
import no.nav.su.se.bakover.service.personhendelser.PersonhendelseService
import no.nav.su.se.bakover.web.services.erLeaderPod
import org.slf4j.LoggerFactory
import java.net.InetAddress
import kotlin.concurrent.fixedRateTimer

internal class PersonhendelseOppgaveJob(
    private val personhendelseService: PersonhendelseService,
    private val leaderPodLookup: LeaderPodLookup,
    private val intervall: Long,
    private val initialDelay: Long,
) {
    private val log = LoggerFactory.getLogger(this::class.java)
    private val jobName = "Opprett personhendelse oppgaver"

    fun schedule() {
        log.info(
            "Starter skeduleringsjobb '$jobName' med intervall: $intervall. Mitt hostnavn er $hostName. Jeg er ${
            if (leaderPodLookup.erLeaderPod(hostname = hostName)) "" else "ikke "
            }leder.",
        )
        fixedRateTimer(
            name = jobName,
            daemon = true,
            period = intervall,
            initialDelay = initialDelay
        ) {
            log.info("Kjører skeduleringsjobb '$jobName'")
            Either.catch {
                if (leaderPodLookup.erLeaderPod(hostname = hostName)) {
                    personhendelseService.opprettOppgaverForPersonhendelser()
                }
            }.mapLeft {
                log.error("Skeduleringsjobb '$jobName' feilet med stacktrace:", it)
            }
        }
    }

    private val hostName = InetAddress.getLocalHost().hostName
}
