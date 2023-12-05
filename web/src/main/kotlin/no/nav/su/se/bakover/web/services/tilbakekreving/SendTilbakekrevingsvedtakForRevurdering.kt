package no.nav.su.se.bakover.web.services.tilbakekreving

import arrow.core.Either
import no.nav.su.se.bakover.common.infrastructure.correlation.withCorrelationId
import no.nav.su.se.bakover.common.infrastructure.jobs.RunCheckFactory
import no.nav.su.se.bakover.common.infrastructure.jobs.shouldRun
import no.nav.su.se.bakover.service.tilbakekreving.TilbakekrevingUnderRevurderingService
import org.jetbrains.kotlin.utils.addToStdlib.ifTrue
import org.slf4j.LoggerFactory
import java.time.Duration
import kotlin.concurrent.fixedRateTimer

/**
 * I en overgangsfase, så lenge vi støtter å vurdere tilbakekreving i revurderingen, må vi ha denne jobben som sender tilbakekrevingsvedtakene til oppdrag for de tilfellene.
 */
internal class SendTilbakekrevingsvedtakForRevurdering(
    private val tilbakekrevingService: TilbakekrevingUnderRevurderingService,
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
                        tilbakekrevingService.sendUteståendeTilbakekrevingsvedtak()
                    }
                }.mapLeft {
                    log.error("Skeduleringsjobb '$jobName' feilet med stacktrace:", it)
                }
            }
        }
    }
}
