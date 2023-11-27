package no.nav.su.se.bakover.database.beregning

import behandling.domain.beregning.fradrag.Fradrag
import behandling.domain.beregning.fradrag.FradragForMåned
import behandling.domain.beregning.fradrag.FradragForPeriode
import behandling.domain.beregning.fradrag.FradragTilhører
import behandling.domain.beregning.fradrag.Fradragstype
import behandling.domain.beregning.fradrag.UtenlandskInntekt
import com.fasterxml.jackson.annotation.JsonProperty
import no.nav.su.se.bakover.common.infrastructure.PeriodeJson
import no.nav.su.se.bakover.common.infrastructure.PeriodeJson.Companion.toJson

/**
 * Vi bruker samme representasjon i databasen for et fradrag for en spesifikk måned eller for en lengre periode.
 */
internal data class PersistertFradrag(
    @JsonProperty("fradragstype")
    val kategori: Fradragstype.Kategori,
    val beskrivelse: String?,
    val månedsbeløp: Double,
    val utenlandskInntekt: UtenlandskInntekt?,
    val periode: PeriodeJson,
    val tilhører: FradragTilhører,
) {
    fun toFradragForMåned(): FradragForMåned {
        return FradragForMåned(
            fradragstype = Fradragstype.from(kategori, beskrivelse),
            månedsbeløp = månedsbeløp,
            måned = periode.tilMåned(),
            utenlandskInntekt = utenlandskInntekt,
            tilhører = tilhører,
        )
    }

    fun toFradragForPeriode(): FradragForPeriode {
        return FradragForPeriode(
            fradragstype = Fradragstype.from(kategori, beskrivelse),
            månedsbeløp = månedsbeløp,
            periode = periode.toPeriode(),
            utenlandskInntekt = utenlandskInntekt,
            tilhører = tilhører,
        )
    }
}

/**
 * Mapper et [Fradrag] til en databaserepresentasjon.
 * Serialiseres/derserialiseres ikke direkte.
 */
internal fun Fradrag.toJson(): PersistertFradrag {
    return PersistertFradrag(
        kategori = fradragstype.kategori,
        beskrivelse = (fradragstype as? Fradragstype.Annet)?.beskrivelse,
        månedsbeløp = månedsbeløp,
        utenlandskInntekt = utenlandskInntekt,
        periode = periode.toJson(),
        tilhører = tilhører,
    )
}
