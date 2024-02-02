package no.nav.su.se.bakover.domain.revurdering.attestering

import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.domain.revurdering.RevurderingId

data class SendTilAttesteringRequest(
    val revurderingId: RevurderingId,
    val saksbehandler: NavIdentBruker.Saksbehandler,
)
