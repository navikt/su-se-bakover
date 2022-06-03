package no.nav.su.se.bakover.service.søknadsbehandling

import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import java.util.UUID

data class VilkårsvurderRequest(
    val behandlingId: UUID,
    val behandlingsinformasjon: Behandlingsinformasjon,
)
