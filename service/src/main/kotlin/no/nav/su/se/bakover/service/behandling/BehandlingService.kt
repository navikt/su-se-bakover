package no.nav.su.se.bakover.service.behandling

import arrow.core.Either
import no.nav.su.se.bakover.domain.Attestant
import no.nav.su.se.bakover.domain.Behandling
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import java.util.UUID

interface BehandlingService {
    fun underkjenn(begrunnelse: String, attestant: Attestant, behandling: Behandling): Either<Behandling.KunneIkkeUnderkjenne, Behandling>
    fun oppdaterBehandlingsinformasjon(behandlingId: UUID, behandlingsinformasjon: Behandlingsinformasjon): Behandling
}
