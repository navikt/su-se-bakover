package no.nav.su.se.bakover.domain.revurdering.tilbakekreving

import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.domain.revurdering.RevurderingId

data class OppdaterTilbakekrevingsbehandlingRequest(
    val revurderingId: RevurderingId,
    val avgjørelse: Avgjørelse,
    val saksbehandler: NavIdentBruker.Saksbehandler,
) {
    enum class Avgjørelse {
        TILBAKEKREV,
        IKKE_TILBAKEKREV,
    }
}
