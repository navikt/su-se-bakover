package no.nav.su.se.bakover.web.services.tilbakekreving

import arrow.core.Either
import arrow.core.getOrHandle
import no.nav.su.se.bakover.domain.nais.LeaderPodLookup
import no.nav.su.se.bakover.service.tilbakekreving.TilbakekrevingService
import org.slf4j.LoggerFactory
import java.net.InetAddress
import java.time.Duration
import kotlin.concurrent.fixedRateTimer

/**
 * Konverterer XML-meldingen fra Oppdrag til domenemodellen
 */
class TilbakekrevingJob(
    private val tilbakekrevingService: TilbakekrevingService,
    private val leaderPodLookup: LeaderPodLookup,
    private val initialDelay: Duration,
    private val intervall: Duration,
) {
    private val log = LoggerFactory.getLogger(this::class.java)
    private val jobName = "Prosesseser kravmelding (tilbakekreving)"
    fun schedule() {
        log.info("Starter skeduleringsjobb '$jobName' med intervall: ${intervall.toMinutes()} min. Mitt hostnavn er $hostName. Jeg er ${if (isLeaderPod()) "" else "ikke "}leder.")
        fixedRateTimer(
            name = jobName,
            daemon = true,
            initialDelay = initialDelay.toMillis(),
            period = intervall.toMillis(),
        ) {
            if (isLeaderPod()) {
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

    private val hostName = InetAddress.getLocalHost().hostName
    private fun isLeaderPod() = leaderPodLookup.amITheLeader(hostName).isRight()
}
