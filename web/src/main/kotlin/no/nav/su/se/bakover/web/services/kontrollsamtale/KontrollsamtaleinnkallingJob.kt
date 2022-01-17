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
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(this::class.java)
    private val jobName = "Utsendelse av kontrollsamtaleinnkallelser"
    private val periode = Duration.of(1, ChronoUnit.DAYS).toMillis()

    private val nå = LocalDateTime.now(clock)
    private val iMorgenKlokka7 = nå.plusDays(1).withHour(7).withMinute(0).withSecond(0)
    private val tidTilKlokka7IMorgen = ChronoUnit.MILLIS.between(nå, iMorgenKlokka7)

    fun schedule() {
        log.info("Starter skeduleringsjobb '$jobName' med intervall: $periode ms og initialDelay: $tidTilKlokka7IMorgen ms. Mitt hostnavn er $hostName. Jeg er ${if (leaderPodLookup.erLeaderPod(hostname = hostName)) "" else "ikke "}leder.")

        fixedRateTimer(
            name = jobName,
            daemon = true,
            period = periode,
            initialDelay = tidTilKlokka7IMorgen
        ) {
            Either.catch {
                if (leaderPodLookup.erLeaderPod(hostname = hostName)) {
                    kontrollsamtaleService.hentPlanlagteKontrollsamtaler(clock).map { kontrollsamtaler ->
                        kontrollsamtaler.forEach {
                            kontrollsamtaleService.kallInn(it.sakId, it)
                        }
                    }
                }
            }.mapLeft {
                log.error("Skeduleringsjobb '$jobName' feilet med stacktrace:", it)
            }
        }
    }

    private val hostName = InetAddress.getLocalHost().hostName
}
