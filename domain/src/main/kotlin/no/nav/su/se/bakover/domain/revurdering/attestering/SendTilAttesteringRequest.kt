package no.nav.su.se.bakover.domain.revurdering.attestering

import no.nav.su.se.bakover.common.NavIdentBruker
import java.util.UUID

data class SendTilAttesteringRequest(
    val revurderingId: UUID,
    val saksbehandler: NavIdentBruker.Saksbehandler,
)
