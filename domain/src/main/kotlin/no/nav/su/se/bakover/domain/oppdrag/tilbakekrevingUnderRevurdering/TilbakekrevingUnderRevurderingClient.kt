package no.nav.su.se.bakover.domain.oppdrag.tilbakekrevingUnderRevurdering

import arrow.core.Either
import tilbakekreving.domain.kravgrunnlag.rått.RåTilbakekrevingsvedtakForsendelse

interface TilbakekrevingClient {
    fun sendTilbakekrevingsvedtakForRevurdering(
        tilbakekrevingsvedtak: TilbakekrevingsvedtakUnderRevurdering,
    ): Either<TilbakekrevingsvedtakForsendelseFeil, RåTilbakekrevingsvedtakForsendelse>
}

/**
 * Dersom vi ikke kunne sende kravgrunnlaget (for å avgjøre om feilutbetalingen skulle føre til tilbakekreving eller ikke) til økonomisystemet
 */
data object TilbakekrevingsvedtakForsendelseFeil
