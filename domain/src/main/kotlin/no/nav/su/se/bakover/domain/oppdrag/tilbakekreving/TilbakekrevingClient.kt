package no.nav.su.se.bakover.domain.oppdrag.tilbakekreving

import arrow.core.Either

interface TilbakekrevingClient {
    fun sendTilbakekrevingsvedtak(tilbakekrevingsvedtak: Tilbakekrevingsvedtak): Either<KunneIkkeSendeKravgrunnlag, RåttTilbakekrevingsvedtak>
}
