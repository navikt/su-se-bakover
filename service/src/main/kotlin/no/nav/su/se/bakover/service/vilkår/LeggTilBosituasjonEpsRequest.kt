package no.nav.su.se.bakover.service.vilkår

import no.nav.su.se.bakover.domain.Fnr
import java.util.UUID

data class LeggTilBosituasjonEpsRequest(
    val behandlingId: UUID,
    val epsFnr: Fnr?,
) {}
