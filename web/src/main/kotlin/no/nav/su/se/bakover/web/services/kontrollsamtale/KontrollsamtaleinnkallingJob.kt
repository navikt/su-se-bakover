package no.nav.su.se.bakover.web.services.kontrollsamtale

import arrow.core.Either
import no.nav.su.se.bakover.domain.nais.LeaderPodLookup
import no.nav.su.se.bakover.service.kontrollsamtale.KontrollsamtaleService
import no.nav.su.se.bakover.web.services.erLeaderPod
import org.slf4j.LoggerFactory
import java.net.InetAddress
import java.time.Clock
import java.time.Duration
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import kotlin.concurrent.fixedRateTimer

/**
 * Jobb som første dag i hver måned sender ut innkallelse til kontrollsamtaler
 */
class KontrollsamtaleinnkallingJob(
    private val leaderPodLookup: LeaderPodLookup,
    private val kontrollsamtaleService: KontrollsamtaleService,
    private val isProd: Boolean,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(this::class.java)
    private val jobName = "Utsendelse av kontrollsamtaleinnkallelser"
    private val periode = if (isProd) Duration.of(1, ChronoUnit.DAYS).toMillis() else Duration.of(5, ChronoUnit.MINUTES).toMillis()

    private val nå = LocalDateTime.now(clock)
    private val iMorgenKlokka7 = nå.plusDays(1).withHour(7).withMinute(0).withSecond(0)
    private val initialDelay = if (isProd) ChronoUnit.MILLIS.between(nå, iMorgenKlokka7) else 0

    fun schedule() {
        // Avventer kall til erLeaderPod i tilfelle den ikke er startet enda.
        log.info("Starter skeduleringsjobb '$jobName' med intervall: $periode ms og initialDelay: $initialDelay ms. Mitt hostnavn er $hostName.")

        fixedRateTimer(
            name = jobName,
            daemon = true,
            period = periode,
            initialDelay = initialDelay,
        ) {
            Either.catch {
                log.debug("Kjører skeduleringsjobb '$jobName'")
                if (leaderPodLookup.erLeaderPod(hostname = hostName)) {
                    kontrollsamtaleService.hentPlanlagteKontrollsamtaler().map { kontrollsamtaler ->
                        kontrollsamtaler.forEach {
                            kontrollsamtaleService.kallInn(it.sakId, it)
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
