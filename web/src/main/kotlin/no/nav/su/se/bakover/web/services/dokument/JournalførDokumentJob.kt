package no.nav.su.se.bakover.web.services.dokument

import arrow.core.Either
import no.nav.su.se.bakover.common.infrastructure.correlation.withCorrelationId
import no.nav.su.se.bakover.common.infrastructure.jobs.RunCheckFactory
import no.nav.su.se.bakover.common.infrastructure.jobs.shouldRun
import no.nav.su.se.bakover.service.journalføring.JournalføringService
import org.jetbrains.kotlin.utils.addToStdlib.ifTrue
import org.slf4j.LoggerFactory
import java.net.InetAddress
import java.time.Duration
import kotlin.concurrent.fixedRateTimer

/**
 * Jobb som med jevne mellomrom sjekker om det eksisterer dokumenter med behov for journalføring.
 *  eksempel dokumenter
 *  [no.nav.su.se.bakover.domain.dokument.Dokumentdistribusjon],
 *  [no.nav.su.se.bakover.domain.skatt.Skattedokument]
 */
internal class JournalførDokumentJob(
    private val initialDelay: Duration,
    private val periode: Duration,
    private val runCheckFactory: RunCheckFactory,
    private val journalføringService: JournalføringService,
) {
    private val jobName = "Journalfør dokumenter"
    private val hostName = InetAddress.getLocalHost().hostName
    private val log = LoggerFactory.getLogger(this::class.java)

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
                listOf(runCheckFactory.leaderPod()).shouldRun().ifTrue {
                    withCorrelationId {
                        log.debug("Kjører skeduleringsjobb '$jobName'")
                        journalføringService.journalfør()
                        log.debug("Fullførte skeduleringsjobb '$jobName'")
                    }
                }
            }.mapLeft {
                log.error("Skeduleringsjobb '$jobName' feilet med stacktrace:", it)
            }
        }
    }
}
