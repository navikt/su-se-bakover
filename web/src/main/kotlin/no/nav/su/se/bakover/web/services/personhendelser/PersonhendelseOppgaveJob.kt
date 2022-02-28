package no.nav.su.se.bakover.web.services.personhendelser

import arrow.core.Either
import no.nav.su.se.bakover.domain.nais.LeaderPodLookup
import no.nav.su.se.bakover.service.personhendelser.PersonhendelseService
import no.nav.su.se.bakover.web.services.erLeaderPod
import org.slf4j.LoggerFactory
import java.net.InetAddress
import java.time.Duration
import kotlin.concurrent.fixedRateTimer

internal class PersonhendelseOppgaveJob(
    private val personhendelseService: PersonhendelseService,
    private val leaderPodLookup: LeaderPodLookup,
    private val periode: Duration,
    private val initialDelay: Duration,
) {
    private val log = LoggerFactory.getLogger(this::class.java)
    private val jobName = "Opprett personhendelse oppgaver"

    fun schedule() {
        // Avventer kall til erLeaderPod i tilfelle den ikke er startet enda.
        log.info("Starter skeduleringsjobb '$jobName' med initialDelay: $initialDelay og periode: $periode. Mitt hostnavn er $hostName.")
        fixedRateTimer(
            name = jobName,
            daemon = true,
            period = periode.toMillis(),
            initialDelay = initialDelay.toMillis(),
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
