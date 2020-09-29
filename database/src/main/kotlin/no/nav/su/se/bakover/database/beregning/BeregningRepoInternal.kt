package no.nav.su.se.bakover.database.beregning

import no.nav.su.se.bakover.database.Session
import no.nav.su.se.bakover.database.hent
import no.nav.su.se.bakover.database.hentListe
import java.util.UUID

internal object BeregningRepoInternal {
    fun hentBeregningForBehandling(behandlingId: UUID, session: Session) =
        "select * from beregning where behandlingId=:id".hent(mapOf("id" to behandlingId), session) {
            it.toBeregning(session)
        }

    fun hentMånedsberegninger(beregningId: UUID, session: Session) =
        "select * from månedsberegning where beregningId = :id".hentListe(mapOf("id" to beregningId), session) {
            it.toMånedsberegning()
        }.toMutableList()

    fun hentFradrag(beregningId: UUID, session: Session) =
        "select * from fradrag where beregningId = :id".hentListe(mapOf("id" to beregningId), session) {
            it.toFradrag()
        }.toMutableList()
}
