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
import no.nav.su.se.bakover.domain.beregning.Fradragstype
import no.nav.su.se.bakover.domain.beregning.Sats
import no.nav.su.se.bakover.domain.beregning.beregning.BeregningFactory
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragFactory
import java.util.UUID

internal object AbstractBeregningRepoInternal {
    fun hentBeregningForBehandling(behandlingId: UUID, session: Session) =
        "select * from beregning where behandlingId=:id".hent(mapOf("id" to behandlingId), session) {
            it.toIBeregning(session)
        }

    // fun hentMånedsberegninger(beregningId: UUID, session: Session) =
    //     "select * from månedsberegning where beregningId = :id".hentListe(mapOf("id" to beregningId), session) {
    //         it.toIMånedsberegning()
    //     }.toMutableList()

    fun hentFradrag(beregningId: UUID, session: Session) =
        "select * from fradrag where beregningId = :id".hentListe(mapOf("id" to beregningId), session) {
            it.toIFradrag()
        }
}

internal fun Row.toIBeregning(session: Session) = BeregningFactory.db(
    id = uuid("id"),
    opprettet = tidspunkt("opprettet"),
    periode = Periode(localDate("fom"), localDate("tom")),
    sats = Sats.valueOf(string("sats")),
    fradrag = AbstractBeregningRepoInternal.hentFradrag(uuid("id"), session)
)
//
// internal fun Row.toIMånedsberegning() = MånedsberegningFactory.db(
//     id = uuid("id"),
//     opprettet = tidspunkt("opprettet"),
//     periode = Periode(localDate("fom"), localDate("tom")),
//     sats = Sats.valueOf(string("sats")),
//     fradrag = int("fradrag") //TODO PROBLEMS EXPECT LIST GOT INT
// )

internal fun Row.toIFradrag() = FradragFactory.db(
    id = uuid("id"),
    opprettet = tidspunkt("opprettet"),
    periode = Periode(localDate("fom"), localDate("tom")),
    beløp = int("beløp").toDouble(), // TODO INT VS DOUBLE
    type = Fradragstype.valueOf(string("fradragstype")),
    utenlandskInntekt = stringOrNull("utenlandskInntekt")?.let { objectMapper.readValue(it) }
)
