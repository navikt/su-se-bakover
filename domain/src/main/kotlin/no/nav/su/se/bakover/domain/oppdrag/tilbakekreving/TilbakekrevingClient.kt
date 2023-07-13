package no.nav.su.se.bakover.domain.oppdrag.tilbakekreving

import arrow.core.Either

interface TilbakekrevingClient {
    fun sendTilbakekrevingsvedtak(tilbakekrevingsvedtak: Tilbakekrevingsvedtak): Either<TilbakekrevingsvedtakForsendelseFeil, RåTilbakekrevingsvedtakForsendelse>
}

/**
 * Dersom vi ikke kunne sende kravgrunnlaget (for å avgjøre om feilutbetalingen skulle føre til tilbakekreving eller ikke) til økonomisystemet
 */
data object TilbakekrevingsvedtakForsendelseFeil
