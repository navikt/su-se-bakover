package no.nav.su.se.bakover.web.services.kontrollsamtale

import arrow.core.Either
import no.nav.su.se.bakover.common.igår
import no.nav.su.se.bakover.common.zoneIdOslo
import no.nav.su.se.bakover.domain.nais.LeaderPodLookup
import no.nav.su.se.bakover.service.kontrollsamtale.UtløptFristForKontrollsamtaleService
import no.nav.su.se.bakover.service.toggles.ToggleService
import no.nav.su.se.bakover.web.services.erLeaderPod
import org.slf4j.LoggerFactory
import java.net.InetAddress
import java.time.Clock
import java.time.Duration
import java.time.LocalTime
import kotlin.concurrent.fixedRateTimer

/**
 * @param ordinærÅpningstidOppdrag begrenser jobben til å kjøre i et tidsintervall hvor vi kan forvente at OS/UR er tilgjengelig for å unngå unødvendige feil ved simulering/oversendelse av utbetalinger.
 */
internal class StansYtelseVedManglendeOppmøteKontrollsamtaleJob(
    private val leaderPodLookup: LeaderPodLookup,
    private val intervall: Duration,
    private val initialDelay: Duration,
    private val toggleService: ToggleService,
    private val service: UtløptFristForKontrollsamtaleService,
    private val ordinærÅpningstidOppdrag: Pair<LocalTime, LocalTime>,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(this::class.java)
    private val jobName = "StansYtelseVedManglendeOppmøteTilKontrollsamtaleJob"

    fun schedule() {
        log.info("Starter skeduleringsjobb '$jobName' med intervall: $intervall i tidsrommet:${ordinærÅpningstidOppdrag.first}-${ordinærÅpningstidOppdrag.second}. Mitt hostnavn er $hostName.")

        fixedRateTimer(
            name = jobName,
            daemon = true,
            period = intervall.toMillis(),
            initialDelay = initialDelay.toMillis(),
        ) {
            Either.catch {
                if (StansYtelseVedManglendeOppmøteKontrollsamtaleJobRunChecker(
                        clock = clock,
                        ordinærÅpningstidOppdrag = ordinærÅpningstidOppdrag,
                        leaderPodLookup = leaderPodLookup,
                        toggleService = toggleService,
                        hostName = hostName,
                    ).shouldRun()
                ) {
                    service.håndterUtløpsdato(igår(clock))
                }
            }.mapLeft {
                log.error("Skeduleringsjobb '$jobName' feilet med stacktrace:", it)
            }
        }
    }

    private val hostName = InetAddress.getLocalHost().hostName
}

internal data class StansYtelseVedManglendeOppmøteKontrollsamtaleJobRunChecker(
    val clock: Clock,
    val ordinærÅpningstidOppdrag: Pair<LocalTime, LocalTime>,
    val leaderPodLookup: LeaderPodLookup,
    val toggleService: ToggleService,
    val hostName: String,
) {
    fun shouldRun(): Boolean {
        val now = LocalTime.now(clock.withZone(zoneIdOslo))
        return now > ordinærÅpningstidOppdrag.first &&
            now < ordinærÅpningstidOppdrag.second &&
            leaderPodLookup.erLeaderPod(hostname = hostName) &&
            toggleService.isEnabled(ToggleService.supstonadAutomatiskStansVedManglendeOppmøteKontrollsamtale)
    }
}
