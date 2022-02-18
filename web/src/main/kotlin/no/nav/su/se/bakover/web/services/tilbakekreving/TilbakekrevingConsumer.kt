package no.nav.su.se.bakover.web.services.tilbakekreving

import arrow.core.getOrHandle
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.RåttKravgrunnlag
import no.nav.su.se.bakover.domain.revurdering.IverksattRevurdering
import no.nav.su.se.bakover.service.revurdering.RevurderingService
import no.nav.su.se.bakover.service.tilbakekreving.TilbakekrevingService
import org.slf4j.LoggerFactory
import java.time.Clock

internal class TilbakekrevingConsumer(
    private val tilbakekrevingService: TilbakekrevingService,
    private val revurderingService: RevurderingService,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(this::class.java)

    internal fun onMessage(xmlMessage: String) {
        // TODO håndter melding om endring av status
        val mottattKravgrunnlag = KravgrunnlagMapper.toDto(xmlMessage)
            .getOrHandle { throw it }

        val utbetalingId = UUID30.fromString(mottattKravgrunnlag.utbetalingId)
        val tilbakekrevingsbehandling = tilbakekrevingService.hentAvventerKravgrunnlag(utbetalingId)
            ?: throw IllegalStateException("Forventet å finne 1 tilbakekrevingsbehandling som avventer kravgrunnlag for utbetalingId: $utbetalingId")

        tilbakekrevingsbehandling.mottattKravgrunnlag(
            kravgrunnlag = RåttKravgrunnlag(xmlMelding = xmlMessage),
            kravgrunnlagMottatt = Tidspunkt.now(clock),
            hentRevurdering = { revurderingId ->
                revurderingService.hentRevurdering(revurderingId) as IverksattRevurdering
            },
            kravgrunnlagMapper = { råttKravgrunnlag ->
                KravgrunnlagMapper.toKravgrunnlg(råttKravgrunnlag).getOrHandle { throw it }
            },
        ).let {
            tilbakekrevingService.lagre(it)
            log.info("Mottatt kravgrunnlag for tilbakekrevingsbehandling: ${tilbakekrevingsbehandling.avgjort.id} for revurdering: ${tilbakekrevingsbehandling.avgjort.revurderingId}")
        }
    }
}
