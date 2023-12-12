package tilbakekreving.presentation.job

import arrow.core.Either
import no.nav.su.se.bakover.common.infrastructure.correlation.withCorrelationId
import no.nav.su.se.bakover.common.infrastructure.jobs.RunCheckFactory
import no.nav.su.se.bakover.common.infrastructure.jobs.shouldRun
import org.jetbrains.kotlin.utils.addToStdlib.ifTrue
import org.slf4j.LoggerFactory
import tilbakekreving.application.service.consumer.GenererDokumentForForhåndsvarselTilbakekrevingKonsument
import tilbakekreving.application.service.consumer.KnyttKravgrunnlagTilSakOgUtbetalingKonsument
import tilbakekreving.application.service.consumer.LukkOppgaveForTilbakekrevingshendelserKonsument
import tilbakekreving.application.service.consumer.OppdaterOppgaveForTilbakekrevingshendelserKonsument
import tilbakekreving.application.service.consumer.OpprettOppgaveForTilbakekrevingshendelserKonsument
import java.time.Duration
import kotlin.concurrent.fixedRateTimer

/**
 * Samlejobb for hendelser som angår kravgrunnlag og tilbakekrevinger.
 */
class Tilbakekrevingsjobber(
    private val knyttKravgrunnlagTilSakOgUtbetalingKonsument: KnyttKravgrunnlagTilSakOgUtbetalingKonsument,
    private val opprettOppgaveKonsument: OpprettOppgaveForTilbakekrevingshendelserKonsument,
    private val lukkOppgaveKonsument: LukkOppgaveForTilbakekrevingshendelserKonsument,
    private val oppdaterOppgaveKonsument: OppdaterOppgaveForTilbakekrevingshendelserKonsument,
    private val genererDokumenterForForhåndsvarselKonsument: GenererDokumentForForhåndsvarselTilbakekrevingKonsument,
    private val initialDelay: Duration,
    private val intervall: Duration,
    private val runCheckFactory: RunCheckFactory,
) {
    private val log = LoggerFactory.getLogger(this::class.java)
    private val jobName = "Tilbakekreving"
    fun schedule() {
        log.info("Starter skeduleringsjobb '$jobName' med intervall: ${intervall.toMinutes()} min")
        fixedRateTimer(
            name = jobName,
            daemon = true,
            initialDelay = initialDelay.toMillis(),
            period = intervall.toMillis(),
        ) {
            listOf(
                runCheckFactory.leaderPod(),
            ).shouldRun().ifTrue {
                kjørTilbakekrevingsjobber()
            }
        }
    }

    /**
     * Kjører alle asynkrone jobber tilknyttet tilbakekrevingsbehandling:
     * - kravgrunnlag
     * - oppgaver
     * - generering av dokumenter
     *
     * Journalføring og distribuering utføres av dokument-modulen.
     */
    fun kjørTilbakekrevingsjobber() {
        Either.catch {
            withCorrelationId { correlationId ->
                knyttKravgrunnlagTilSakOgUtbetalingKonsument.knyttKravgrunnlagTilSakOgUtbetaling(correlationId)
                opprettOppgaveKonsument.opprettOppgaver(correlationId)
                genererDokumenterForForhåndsvarselKonsument.genererDokumenter(correlationId)
                lukkOppgaveKonsument.lukkOppgaver(correlationId)
                oppdaterOppgaveKonsument.oppdaterOppgaver(correlationId)
            }
        }.mapLeft {
            // Dette er bare en guard - hver jobb skal håndtere feil selv (og ingen skal kaste videre hit).
            log.error("Skeduleringsjobb '$jobName' feilet med stacktrace:", it)
        }
    }
}
