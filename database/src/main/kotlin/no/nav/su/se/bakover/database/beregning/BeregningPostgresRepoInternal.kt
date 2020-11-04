package no.nav.su.se.bakover.database.beregning

import com.fasterxml.jackson.module.kotlin.readValue
import kotliquery.Row
import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.database.Session
import no.nav.su.se.bakover.database.hent
import no.nav.su.se.bakover.database.hentListe
import no.nav.su.se.bakover.database.tidspunkt
import no.nav.su.se.bakover.database.uuid
import no.nav.su.se.bakover.domain.beregning.BeregningFactory
import no.nav.su.se.bakover.domain.beregning.Sats
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragFactory
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import java.util.UUID

internal object BeregningPostgresRepoInternal {
    fun hentBeregningForBehandling(behandlingId: UUID, session: Session) =
        "select * from beregning where behandlingId=:id".hent(mapOf("id" to behandlingId), session) {
            it.toIBeregning(session)
        }

    fun hentFradrag(beregningId: UUID, session: Session) =
        "select * from fradrag where beregningId = :id".hentListe(mapOf("id" to beregningId), session) {
            it.toIFradrag()
        }
}

internal fun Row.toIBeregning(session: Session) = BeregningFactory.persistert(
    id = uuid("id"),
    opprettet = tidspunkt("opprettet"),
    periode = Periode(localDate("fom"), localDate("tom")),
    sats = Sats.valueOf(string("sats")),
    fradrag = BeregningPostgresRepoInternal.hentFradrag(uuid("id"), session)
)

internal fun Row.toIFradrag() = FradragFactory.persistert(
    id = uuid("id"),
    opprettet = tidspunkt("opprettet"),
    periode = Periode(localDate("fom"), localDate("tom")),
    beløp = double("beløp"),
    type = Fradragstype.valueOf(string("fradragstype")),
    utenlandskInntekt = stringOrNull("utenlandskInntekt")?.let { objectMapper.readValue(it) }
)
