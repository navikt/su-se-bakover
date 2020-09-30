package no.nav.su.se.bakover.database.behandling

import kotliquery.using
import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.database.behandling.BehandlingRepoInternal.hentBehandling
import no.nav.su.se.bakover.database.oppdatering
import no.nav.su.se.bakover.database.sessionOf
import no.nav.su.se.bakover.domain.Behandling
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import java.util.UUID
import javax.sql.DataSource

internal class BehandlingPostgresRepo(
    private val dataSource: DataSource
) : BehandlingRepo {
    override fun hentBehandling(behandlingId: UUID): Behandling? =
        using(sessionOf(dataSource)) { hentBehandling(behandlingId, it) }

    override fun oppdaterBehandlingsinformasjon(
        behandlingId: UUID,
        behandlingsinformasjon: Behandlingsinformasjon
    ): Behandling {
        "update behandling set behandlingsinformasjon = to_json(:behandlingsinformasjon::json) where id = :id".oppdatering(
            mapOf(
                "id" to behandlingId,
                "behandlingsinformasjon" to objectMapper.writeValueAsString(behandlingsinformasjon)
            )
        )
        return hentBehandling(behandlingId)!!
    }

    private fun String.oppdatering(params: Map<String, Any?>) {
        using(sessionOf(dataSource)) {
            this.oppdatering(params, it)
        }
    }

    override fun oppdaterBehandlingStatus(
        behandlingId: UUID,
        status: Behandling.BehandlingsStatus
    ): Behandling {
        "update behandling set status = :status where id = :id".oppdatering(
            mapOf(
                "id" to behandlingId,
                "status" to status.name
            )
        )
        return hentBehandling(behandlingId)!!
    }
}
