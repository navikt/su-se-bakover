package no.nav.su.se.bakover.client.stubs.oppdrag

import arrow.core.Either
import arrow.core.right
import no.nav.su.se.bakover.client.oppdrag.tilbakekreving.TilbakekrevingsvedtakMapper
import no.nav.su.se.bakover.client.oppdrag.tilbakekreving.mapToTilbakekrevingsvedtakRequest
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.KunneIkkeSendeKravgrunnlag
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.RåttTilbakekrevingsvedtak
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.TilbakekrevingClient
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.Tilbakekrevingsvedtak

object TilbakekrevingClientStub : TilbakekrevingClient {
    override fun sendTilbakekrevingsvedtak(tilbakekrevingsvedtak: Tilbakekrevingsvedtak): Either<KunneIkkeSendeKravgrunnlag, RåttTilbakekrevingsvedtak> {
        return mapToTilbakekrevingsvedtakRequest(tilbakekrevingsvedtak).let {
            TilbakekrevingsvedtakMapper.map(it)
        }.right()
    }
}
