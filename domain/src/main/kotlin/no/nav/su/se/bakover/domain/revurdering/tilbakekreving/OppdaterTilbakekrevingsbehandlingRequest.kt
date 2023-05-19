package no.nav.su.se.bakover.domain.revurdering.tilbakekreving

import no.nav.su.se.bakover.common.ident.NavIdentBruker
import java.util.UUID

data class OppdaterTilbakekrevingsbehandlingRequest(
    val revurderingId: UUID,
    val avgjørelse: Avgjørelse,
    val saksbehandler: NavIdentBruker.Saksbehandler,
) {
    enum class Avgjørelse {
        TILBAKEKREV,
        IKKE_TILBAKEKREV,
    }
}
