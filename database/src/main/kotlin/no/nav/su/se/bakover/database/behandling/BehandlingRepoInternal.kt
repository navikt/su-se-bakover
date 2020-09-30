package no.nav.su.se.bakover.database.behandling

import no.nav.su.se.bakover.database.Session
import no.nav.su.se.bakover.database.hent
import no.nav.su.se.bakover.database.hentListe
import no.nav.su.se.bakover.domain.Behandling
import java.util.UUID

internal object BehandlingRepoInternal {
    fun hentBehandling(behandlingId: UUID, session: Session): Behandling? =
        "select * from behandling where id=:id"
            .hent(mapOf("id" to behandlingId), session) { row ->
                row.toBehandling(session)
            }

    fun hentBehandlingerForSak(sakId: UUID, session: Session) = "select * from behandling where sakId=:sakId"
        .hentListe(mapOf("sakId" to sakId), session) {
            it.toBehandling(session)
        }.toMutableList()
}
