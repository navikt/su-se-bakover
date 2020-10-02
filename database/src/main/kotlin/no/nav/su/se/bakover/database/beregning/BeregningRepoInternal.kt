package no.nav.su.se.bakover.database.beregning

import com.fasterxml.jackson.module.kotlin.readValue
import kotliquery.Row
import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.database.Session
import no.nav.su.se.bakover.database.hent
import no.nav.su.se.bakover.database.hentListe
import no.nav.su.se.bakover.database.tidspunkt
import no.nav.su.se.bakover.database.uuid
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.beregning.Fradrag
import no.nav.su.se.bakover.domain.beregning.Fradragstype
import no.nav.su.se.bakover.domain.beregning.InntektDelerAvPeriode
import no.nav.su.se.bakover.domain.beregning.Månedsberegning
import no.nav.su.se.bakover.domain.beregning.Sats
import no.nav.su.se.bakover.domain.beregning.UtenlandskInntekt
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

internal fun Row.toBeregning(session: Session) = Beregning(
    id = uuid("id"),
    opprettet = tidspunkt("opprettet"),
    fraOgMed = localDate("fom"),
    tilOgMed = localDate("tom"),
    sats = Sats.valueOf(string("sats")),
    månedsberegninger = BeregningRepoInternal.hentMånedsberegninger(uuid("id"), session),
    fradrag = BeregningRepoInternal.hentFradrag(uuid("id"), session),
    forventetInntekt = int("forventetInntekt")
)

internal fun Row.toMånedsberegning() = Månedsberegning(
    id = uuid("id"),
    opprettet = tidspunkt("opprettet"),
    fraOgMed = localDate("fom"),
    tilOgMed = localDate("tom"),
    grunnbeløp = int("grunnbeløp"),
    sats = Sats.valueOf(string("sats")),
    beløp = int("beløp"),
    fradrag = int("fradrag")
)

internal fun Row.toFradrag() = Fradrag(
    id = uuid("id"),
    beløp = int("beløp"),
    type = Fradragstype.valueOf(string("fradragstype")),
    utenlandskInntekt = objectMapper.readValue(string("utenlandskInntekt")) as UtenlandskInntekt?,
    inntektDelerAvPeriode = objectMapper.readValue(string("inntektDelerAvPeriode")) as InntektDelerAvPeriode?,
)
