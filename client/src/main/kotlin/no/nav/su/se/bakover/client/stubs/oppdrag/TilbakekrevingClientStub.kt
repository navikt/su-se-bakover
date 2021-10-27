package no.nav.su.se.bakover.client.stubs.oppdrag

import arrow.core.Either
import arrow.core.right
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.KunneIkkeSendeKravgrunnlag
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.TilbakekrevingClient
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.Tilbakekrevingsvedtak

object TilbakekrevingClientStub : TilbakekrevingClient {
    override fun avgjørKravgrunnlag(avgjørelse: Tilbakekrevingsvedtak): Either<KunneIkkeSendeKravgrunnlag, Unit> {
        return Unit.right()
    }
}
