package no.nav.su.se.bakover.domain.oppdrag.tilbakekreving

import arrow.core.Either

interface TilbakekrevingClient {
    fun avgjørKravgrunnlag(avgjørelse: Tilbakekrevingsvedtak): Either<KunneIkkeSendeKravgrunnlag, Unit>
}
