package no.nav.su.se.bakover.web.services.kontrollsamtale

import arrow.core.Either
import no.nav.su.se.bakover.common.idag
import no.nav.su.se.bakover.domain.nais.LeaderPodLookup
import no.nav.su.se.bakover.service.kontrollsamtale.UtløptFristForKontrollsamtaleService
import no.nav.su.se.bakover.service.toggles.ToggleService
import no.nav.su.se.bakover.web.services.erLeaderPod
import org.slf4j.LoggerFactory
import java.net.InetAddress
import java.time.Clock
import java.time.Duration
import kotlin.concurrent.fixedRateTimer

internal class StansYtelseVedManglendeOppmøteKontrollsamtaleJob(
    private val leaderPodLookup: LeaderPodLookup,
    private val intervall: Duration,
    private val initialDelay: Duration,
    private val toggleService: ToggleService,
    private val service: UtløptFristForKontrollsamtaleService,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(this::class.java)

    private val jobName = "StansYtelseVedManglendeOppmøteTilKontrollsamtaleJob"

    fun schedule() {
        log.info("Starter skeduleringsjobb '$jobName' med periode: $intervall. Mitt hostnavn er $hostName.")

        fixedRateTimer(
            name = jobName,
            daemon = true,
            period = intervall.toMillis(),
            initialDelay = initialDelay.toMillis(),
        ) {
            Either.catch {
                if (leaderPodLookup.erLeaderPod(hostname = hostName) && toggleService.isEnabled(ToggleService.supstonadAutomatiskStansVedManglendeOppmøteKontrollsamtale)) {
                    service.håndterUtløpsdato(idag(clock))
                }
            }.mapLeft {
                log.error("Skeduleringsjobb '$jobName' feilet med stacktrace:", it)
            }
        }
    }

    private val hostName = InetAddress.getLocalHost().hostName
}
