package no.nav.su.se.bakover.service.klage

import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.domain.attestering.UnderkjennAttesteringsgrunnBehandling
import java.util.UUID

data class UnderkjennKlageRequest(
    val klageId: UUID,
    val attestant: NavIdentBruker.Attestant,
    val grunn: UnderkjennAttesteringsgrunnBehandling,
    val kommentar: String,
)
