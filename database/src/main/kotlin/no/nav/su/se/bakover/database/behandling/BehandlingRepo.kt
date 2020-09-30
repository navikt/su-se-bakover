package no.nav.su.se.bakover.database.behandling

import no.nav.su.se.bakover.domain.Behandling
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import java.util.UUID

interface BehandlingRepo {
    fun hentBehandling(behandlingId: UUID): Behandling?
    fun oppdaterBehandlingsinformasjon(behandlingId: UUID, behandlingsinformasjon: Behandlingsinformasjon): Behandling
    fun oppdaterBehandlingStatus(behandlingId: UUID, status: Behandling.BehandlingsStatus): Behandling
}
