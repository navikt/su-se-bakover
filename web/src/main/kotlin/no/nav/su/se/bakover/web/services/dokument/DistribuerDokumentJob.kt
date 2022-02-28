package no.nav.su.se.bakover.web.services.dokument

import arrow.core.Either
import no.nav.su.se.bakover.domain.nais.LeaderPodLookup
import no.nav.su.se.bakover.service.brev.BrevService
import no.nav.su.se.bakover.web.services.erLeaderPod
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import java.net.InetAddress
import java.time.Duration
import java.util.UUID
import kotlin.concurrent.fixedRateTimer

/**
 * Jobb som med jevne mellomrom sjekker om det eksisterer [no.nav.su.se.bakover.domain.dokument.Dokumentdistribusjon]
 * med behov for journalføring eller bestilling av brev.
 */
class DistribuerDokumentJob(
    private val brevService: BrevService,
    private val leaderPodLookup: LeaderPodLookup,
    private val initialDelay: Duration,
    private val periode: Duration,
) {
    private val log = LoggerFactory.getLogger(this::class.java)
    private val jobName = "Journalfør og bestill brevdistribusjon"

    fun schedule() {
        // Avventer kall til erLeaderPod i tilfelle den ikke er startet enda.
        log.info("Starter skeduleringsjobb '$jobName' med initialDelay $initialDelay og periode $periode. Mitt hostnavn er $hostName.")

        fixedRateTimer(
            name = jobName,
            daemon = true,
            period = periode.toMillis(),
            initialDelay = initialDelay.toMillis(),
        ) {
            Either.catch {
                if (leaderPodLookup.erLeaderPod(hostname = hostName)) {
                    // Ktor legger på X-Correlation-ID for web-requests, men vi har ikke noe tilsvarende automagi for meldingskøen.
                    MDC.put("X-Correlation-ID", UUID.randomUUID().toString())
                    // Disse er debug siden jobben kjører hvert minutt.
                    log.debug("Kjører skeduleringsjobb '$jobName'")
                    brevService.journalførOgDistribuerUtgåendeDokumenter()
                    log.debug("Fullførte skeduleringsjobb '$jobName'")
                }
            }.mapLeft {
                log.error("Skeduleringsjobb '$jobName' feilet med stacktrace:", it)
            }
        }
    }

    private val hostName = InetAddress.getLocalHost().hostName
}
