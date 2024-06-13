package tilbakekreving.presentation.job

import no.nav.su.se.bakover.common.infrastructure.job.RunCheckFactory
import no.nav.su.se.bakover.common.infrastructure.job.StoppableJob
import no.nav.su.se.bakover.common.infrastructure.job.startStoppableJob
import org.slf4j.LoggerFactory
import tilbakekreving.application.service.consumer.GenererDokumentForForhåndsvarselTilbakekrevingKonsument
import tilbakekreving.application.service.consumer.GenererVedtaksbrevTilbakekrevingKonsument
import tilbakekreving.application.service.consumer.KnyttKravgrunnlagTilSakOgUtbetalingKonsument
import tilbakekreving.application.service.consumer.LukkOppgaveForTilbakekrevingshendelserKonsument
import tilbakekreving.application.service.consumer.OppdaterOppgaveForTilbakekrevingshendelserKonsument
import tilbakekreving.application.service.consumer.OpprettOppgaveForTilbakekrevingshendelserKonsument
import java.time.Duration

/** Samlejobb for hendelser som angår kravgrunnlag og tilbakekrevinger. */
class Tilbakekrevingsjobber(
    private val stoppableJob: StoppableJob,
) : StoppableJob by stoppableJob {

    companion object {
        /**
         * Kjører alle asynkrone jobber tilknyttet tilbakekrevingsbehandling:
         *   - kravgrunnlag
         *   - oppgaver
         *   - generering av dokumenter
         * Journalføring og distribuering utføres av dokument-modulen.
         */
        fun startJob(
            knyttKravgrunnlagTilSakOgUtbetalingKonsument: KnyttKravgrunnlagTilSakOgUtbetalingKonsument,
            opprettOppgaveKonsument: OpprettOppgaveForTilbakekrevingshendelserKonsument,
            lukkOppgaveKonsument: LukkOppgaveForTilbakekrevingshendelserKonsument,
            oppdaterOppgaveKonsument: OppdaterOppgaveForTilbakekrevingshendelserKonsument,
            genererDokumenterForForhåndsvarselKonsument: GenererDokumentForForhåndsvarselTilbakekrevingKonsument,
            genererVedtaksbrevTilbakekrevingKonsument: GenererVedtaksbrevTilbakekrevingKonsument,
            initialDelay: Duration,
            intervall: Duration,
            runCheckFactory: RunCheckFactory,
        ): Tilbakekrevingsjobber {
            return startStoppableJob(
                jobName = "Tilbakekreving",
                initialDelay = initialDelay,
                intervall = intervall,
                log = LoggerFactory.getLogger(Tilbakekrevingsjobber::class.java),
                runJobCheck = listOf(runCheckFactory.leaderPod()),
            ) { correlationId ->
                knyttKravgrunnlagTilSakOgUtbetalingKonsument.knyttKravgrunnlagTilSakOgUtbetaling(correlationId)
                opprettOppgaveKonsument.opprettOppgaver(correlationId)
                genererDokumenterForForhåndsvarselKonsument.genererDokumenter(correlationId)
                lukkOppgaveKonsument.lukkOppgaver(correlationId)
                oppdaterOppgaveKonsument.oppdaterOppgaver(correlationId)
                genererVedtaksbrevTilbakekrevingKonsument.genererVedtaksbrev(correlationId)
            }.let {
                Tilbakekrevingsjobber(it)
            }
        }
    }
}
