package no.nav.su.se.bakover.domain.søknadsbehandling.iverksett

import no.nav.su.se.bakover.domain.behandling.Attestering
import java.util.UUID

data class IverksettRequest(
    val behandlingId: UUID,
    val attestering: Attestering,
)
