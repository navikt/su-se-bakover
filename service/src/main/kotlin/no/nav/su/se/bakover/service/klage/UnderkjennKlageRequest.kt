package no.nav.su.se.bakover.service.klage

import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.behandling.Attestering
import java.util.UUID

data class UnderkjennKlageRequest(
    val klageId: UUID,
    val attestant: NavIdentBruker.Attestant,
    val grunn: Attestering.Underkjent.Grunn,
    val kommentar: String,
)
