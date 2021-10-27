package no.nav.su.se.bakover.web.services.tilbakekreving

import arrow.core.Either
import no.nav.su.se.bakover.domain.nais.LeaderPodLookup
import no.nav.su.se.bakover.service.tilbakekreving.TilbakekrevingService
import org.slf4j.LoggerFactory
import java.net.InetAddress
import kotlin.concurrent.fixedRateTimer

/**
 * Konverterer XML-meldingen fra Oppdrag til domenemodellen
 */
class TilbakekrevingJob(
    private val tilbakekrevingService: TilbakekrevingService,
    private val leaderPodLookup: LeaderPodLookup,
    private val intervall: Long
) {
    private val log = LoggerFactory.getLogger(this::class.java)
    private val jobName = "Prosesseser kravmelding (tilbakekreving)"
    fun schedule() {
        log.info("Starter skeduleringsjobb '$jobName' med intervall: $intervall. Mitt hostnavn er $hostName. Jeg er ${if (isLeaderPod()) "" else "ikke "}leder.")
        fixedRateTimer(
            name = jobName,
            daemon = true,
            period = intervall,
        ) {
            if (isLeaderPod()) {
                log.info("Kjører skeduleringsjobb '$jobName'")
                Either.catch {
                    tilbakekrevingService.sendTilbakekrevinger()
                }.mapLeft {
                    log.error("Skeduleringsjobb '$jobName' feilet med stacktrace:", it)
                }
            }
        }
    }

    private val hostName = InetAddress.getLocalHost().hostName
    private fun isLeaderPod() = leaderPodLookup.amITheLeader(hostName).isRight()
    fun onMessage(message: String) {
        TilbakekrevingXmlMapper.toDto(message)
        // TODO jah: Hent tilbakekrevingingen aksjonspunkter og merge med denne før man sender til oppdrag
    }
}
