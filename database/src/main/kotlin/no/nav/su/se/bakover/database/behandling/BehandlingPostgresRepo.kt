package no.nav.su.se.bakover.database.behandling

import kotliquery.using
import no.nav.su.se.bakover.database.behandling.BehandlingRepoInternal.hentBehandling
import no.nav.su.se.bakover.database.sessionOf
import no.nav.su.se.bakover.domain.Behandling
import java.util.UUID
import javax.sql.DataSource

internal class BehandlingPostgresRepo(
    private val dataSource: DataSource
) : BehandlingRepo {
    override fun hentBehandling(behandlingId: UUID): Behandling? =
        using(sessionOf(dataSource)) { hentBehandling(behandlingId, it) }
}
