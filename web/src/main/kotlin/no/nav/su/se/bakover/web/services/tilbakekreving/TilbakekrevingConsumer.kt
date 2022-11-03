package no.nav.su.se.bakover.web.services.tilbakekreving

import arrow.core.getOrHandle
import no.nav.su.se.bakover.common.CorrelationId
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.RåttKravgrunnlag
import no.nav.su.se.bakover.domain.revurdering.IverksattRevurdering
import no.nav.su.se.bakover.service.revurdering.RevurderingService
import no.nav.su.se.bakover.service.tilbakekreving.TilbakekrevingService
import org.slf4j.LoggerFactory
import java.time.Clock

class TilbakekrevingConsumer(
    private val tilbakekrevingService: TilbakekrevingService,
    private val revurderingService: RevurderingService,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(this::class.java)

    fun onMessage(xmlMessage: String) {
        CorrelationId.withCorrelationId {
            val mottattMelding = TilbakekrevingsmeldingMapper.toDto(xmlMessage)
                .getOrHandle { throw it }

            when (mottattMelding) {
                is KravgrunnlagRootDto -> {
                    mottattMelding.kravgrunnlagDto.let {
                        val utbetalingId = UUID30.fromString(it.utbetalingId)
                        val tilbakekrevingsbehandling = tilbakekrevingService.hentAvventerKravgrunnlag(utbetalingId)
                            ?: throw IllegalStateException("Forventet å finne 1 tilbakekrevingsbehandling som avventer kravgrunnlag for utbetalingId: $utbetalingId")

                        tilbakekrevingsbehandling.mottattKravgrunnlag(
                            kravgrunnlag = RåttKravgrunnlag(xmlMelding = xmlMessage),
                            kravgrunnlagMottatt = Tidspunkt.now(clock),
                            hentRevurdering = { revurderingId ->
                                revurderingService.hentRevurdering(revurderingId) as IverksattRevurdering
                            },
                            kravgrunnlagMapper = { råttKravgrunnlag ->
                                TilbakekrevingsmeldingMapper.toKravgrunnlg(råttKravgrunnlag).getOrHandle { throw it }
                            },
                        ).let {
                            tilbakekrevingService.lagre(it)
                            log.info("Mottatt kravgrunnlag for tilbakekrevingsbehandling: ${tilbakekrevingsbehandling.avgjort.id} for revurdering: ${tilbakekrevingsbehandling.avgjort.revurderingId}")
                        }
                    }
                }

                is KravgrunnlagStatusendringRootDto -> {
                    mottattMelding.endringKravOgVedtakstatus.let {
                        log.error("Mottok melding om endring i kravgrunnlag for tilbakekrevingsvedtak: ${it.vedtakId}, saksnummer:${it.fagsystemId} - prosessering av endringsmeldinger er ikke definert.")
                    }
                }
            }
        }
    }
}
