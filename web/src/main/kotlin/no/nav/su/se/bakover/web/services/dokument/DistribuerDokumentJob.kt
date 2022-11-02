package no.nav.su.se.bakover.web.services.dokument

import arrow.core.Either
import no.nav.su.se.bakover.common.CorrelationId.Companion.withCorrelationId
import no.nav.su.se.bakover.service.brev.BrevService
import no.nav.su.se.bakover.web.services.RunCheckFactory
import no.nav.su.se.bakover.web.services.shouldRun
import org.jetbrains.kotlin.utils.addToStdlib.ifTrue
import org.slf4j.LoggerFactory
import java.net.InetAddress
import java.time.Duration
import kotlin.concurrent.fixedRateTimer

/**
 * Jobb som med jevne mellomrom sjekker om det eksisterer [no.nav.su.se.bakover.domain.dokument.Dokumentdistribusjon]
 * med behov for journalføring eller bestilling av brev.
 */
internal class DistribuerDokumentJob(
    private val brevService: BrevService,
    private val initialDelay: Duration,
    private val periode: Duration,
    private val runCheckFactory: RunCheckFactory,
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
                listOf(runCheckFactory.leaderPod())
                    .shouldRun()
                    .ifTrue {
                        withCorrelationId {
                            // Disse er debug siden jobben kjører hvert minutt.
                            log.debug("Kjører skeduleringsjobb '$jobName'")
                            brevService.journalførOgDistribuerUtgåendeDokumenter()
                            log.debug("Fullførte skeduleringsjobb '$jobName'")
                        }
                    }
            }.mapLeft {
                log.error("Skeduleringsjobb '$jobName' feilet med stacktrace:", it)
            }
        }
    }

    private val hostName = InetAddress.getLocalHost().hostName
}
