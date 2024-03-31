package no.nav.su.se.bakover.service.klage

import behandling.domain.UnderkjennAttesteringsgrunnBehandling
import behandling.klage.domain.KlageId
import no.nav.su.se.bakover.common.ident.NavIdentBruker

data class UnderkjennKlageRequest(
    val klageId: KlageId,
    val attestant: NavIdentBruker.Attestant,
    val grunn: UnderkjennAttesteringsgrunnBehandling,
    val kommentar: String,
)
