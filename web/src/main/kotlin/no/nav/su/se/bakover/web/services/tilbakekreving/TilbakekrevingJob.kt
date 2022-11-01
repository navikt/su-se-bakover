package no.nav.su.se.bakover.web.services.tilbakekreving

import arrow.core.Either
import arrow.core.getOrHandle
import no.nav.su.se.bakover.common.CorrelationId.Companion.withCorrelationId
import no.nav.su.se.bakover.service.tilbakekreving.TilbakekrevingService
import no.nav.su.se.bakover.web.services.RunCheckFactory
import no.nav.su.se.bakover.web.services.shouldRun
import org.jetbrains.kotlin.utils.addToStdlib.ifTrue
import org.slf4j.LoggerFactory
import java.time.Duration
import kotlin.concurrent.fixedRateTimer

/**
 * Konverterer XML-meldingen fra Oppdrag til domenemodellen
 */
internal class TilbakekrevingJob(
    private val tilbakekrevingService: TilbakekrevingService,
    private val initialDelay: Duration,
    private val intervall: Duration,
    private val runCheckFactory: RunCheckFactory,
) {
    private val log = LoggerFactory.getLogger(this::class.java)
    private val jobName = "Prosesseser kravmelding (tilbakekreving)"
    fun schedule() {
        log.info("Starter skeduleringsjobb '$jobName' med intervall: ${intervall.toMinutes()} min")
        fixedRateTimer(
            name = jobName,
            daemon = true,
            initialDelay = initialDelay.toMillis(),
            period = intervall.toMillis(),
        ) {
            listOf(
                runCheckFactory.åpningstidStormaskin(),
                runCheckFactory.leaderPod(),
            ).shouldRun().ifTrue {
                Either.catch {
                    withCorrelationId {
                        tilbakekrevingService.sendTilbakekrevingsvedtak() { råttKravgrunnlag ->
                            TilbakekrevingsmeldingMapper.toKravgrunnlg(råttKravgrunnlag)
                                .getOrHandle { throw it }
                        }
                    }
                }.mapLeft {
                    log.error("Skeduleringsjobb '$jobName' feilet med stacktrace:", it)
                }
            }
        }
    }
}
