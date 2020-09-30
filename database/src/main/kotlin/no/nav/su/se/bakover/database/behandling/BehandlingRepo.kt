package no.nav.su.se.bakover.database.behandling

import no.nav.su.se.bakover.domain.Behandling
import java.util.UUID

interface BehandlingRepo {
    fun hentBehandling(behandlingId: UUID): Behandling?
}
