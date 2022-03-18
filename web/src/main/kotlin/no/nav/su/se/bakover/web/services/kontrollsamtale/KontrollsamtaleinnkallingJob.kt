package no.nav.su.se.bakover.web.services.kontrollsamtale

import arrow.core.Either
import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.domain.nais.LeaderPodLookup
import no.nav.su.se.bakover.service.kontrollsamtale.KontrollsamtaleService
import no.nav.su.se.bakover.web.services.erLeaderPod
import org.slf4j.LoggerFactory
import java.net.InetAddress
import java.time.Duration
import java.util.Date
import kotlin.concurrent.fixedRateTimer

/**
 * Jobb som første dag i hver måned sender ut innkallelse til kontrollsamtaler
 */
class KontrollsamtaleinnkallingJob(
    private val leaderPodLookup: LeaderPodLookup,
    private val kontrollsamtaleService: KontrollsamtaleService,
    private val starttidspunkt: Date,
    private val periode: Duration,
    private val sessionFactory: SessionFactory,
) {
    private val log = LoggerFactory.getLogger(this::class.java)

    private val jobName = "Utsendelse av kontrollsamtaleinnkallelser"

    fun schedule() {
        // Avventer kall til erLeaderPod i tilfelle den ikke er startet enda.
        log.info("Starter skeduleringsjobb '$jobName' med periode: $periode og starttidspunkt: $starttidspunkt. Mitt hostnavn er $hostName.")

        fixedRateTimer(
            name = jobName,
            daemon = true,
            startAt = starttidspunkt,
            period = periode.toMillis(),
        ) {
            Either.catch {
                log.debug("Kjører skeduleringsjobb '$jobName'")
                if (leaderPodLookup.erLeaderPod(hostname = hostName)) {
                    kontrollsamtaleService.hentPlanlagteKontrollsamtaler().map { kontrollsamtaler ->
                        kontrollsamtaler.forEach {
                            // En kan se på hver kontrollsamtale som isolert fra hverandre.
                            // Vi ønsker ikke rulle tilbakealle kontrollsamtalene dersom en feiler.
                            kontrollsamtaleService.kallInn(it.sakId, it, sessionFactory.newTransactionContext())
                        }
                    }
                }
                log.debug("Fullførte skeduleringsjobb '$jobName'")
            }.mapLeft {
                log.error("Skeduleringsjobb '$jobName' feilet med stacktrace:", it)
            }
        }
    }

    private val hostName = InetAddress.getLocalHost().hostName
}
