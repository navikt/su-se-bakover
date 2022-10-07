package no.nav.su.se.bakover.web.services.tilbakekreving

import arrow.core.Either
import arrow.core.getOrHandle
import no.nav.su.se.bakover.common.zoneIdOslo
import no.nav.su.se.bakover.domain.nais.LeaderPodLookup
import no.nav.su.se.bakover.service.tilbakekreving.TilbakekrevingService
import org.slf4j.LoggerFactory
import java.net.InetAddress
import java.time.Clock
import java.time.Duration
import java.time.LocalTime
import kotlin.concurrent.fixedRateTimer

/**
 * Konverterer XML-meldingen fra Oppdrag til domenemodellen
 */
class TilbakekrevingJob(
    private val tilbakekrevingService: TilbakekrevingService,
    private val leaderPodLookup: LeaderPodLookup,
    private val initialDelay: Duration,
    private val intervall: Duration,
    private val ordinærÅpningstidOppdrag: Pair<LocalTime, LocalTime>,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(this::class.java)
    private val jobName = "Prosesseser kravmelding (tilbakekreving)"
    fun schedule() {
        log.info("Starter skeduleringsjobb '$jobName' med intervall: ${intervall.toMinutes()} min, i tidsrommet:${ordinærÅpningstidOppdrag.first}-${ordinærÅpningstidOppdrag.second}. Mitt hostnavn er $hostName. Jeg er ${if (isLeaderPod()) "" else "ikke "}leder.")
        fixedRateTimer(
            name = jobName,
            daemon = true,
            initialDelay = initialDelay.toMillis(),
            period = intervall.toMillis(),
        ) {
            if (shouldRun()) {
                log.info("Kjører skeduleringsjobb '$jobName'")
                Either.catch {
                    tilbakekrevingService.sendTilbakekrevingsvedtak() { råttKravgrunnlag ->
                        TilbakekrevingsmeldingMapper.toKravgrunnlg(råttKravgrunnlag)
                            .getOrHandle { throw it }
                    }
                }.mapLeft {
                    log.error("Skeduleringsjobb '$jobName' feilet med stacktrace:", it)
                }
            }
        }
    }

    private fun shouldRun(): Boolean {
        val now = LocalTime.now(clock.withZone(zoneIdOslo))
        return isLeaderPod() &&
            now > ordinærÅpningstidOppdrag.first &&
            now < ordinærÅpningstidOppdrag.second
    }

    private val hostName = InetAddress.getLocalHost().hostName
    private fun isLeaderPod() = leaderPodLookup.amITheLeader(hostName).isRight()
}
