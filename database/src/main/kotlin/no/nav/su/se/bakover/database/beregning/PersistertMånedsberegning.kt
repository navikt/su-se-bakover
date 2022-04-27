package no.nav.su.se.bakover.database.beregning

import no.nav.su.se.bakover.common.periode.MånedsperiodeJson
import no.nav.su.se.bakover.common.periode.MånedsperiodeJson.Companion.toJson
import no.nav.su.se.bakover.domain.beregning.BeregningForMåned
import no.nav.su.se.bakover.domain.beregning.Merknader
import no.nav.su.se.bakover.domain.beregning.Månedsberegning
import no.nav.su.se.bakover.domain.beregning.Sats

internal data class PersistertMånedsberegning(
    val sumYtelse: Int,
    val sumFradrag: Double,
    val benyttetGrunnbeløp: Int,
    val sats: Sats,
    val satsbeløp: Double,
    val fradrag: List<PersistertFradrag>,
    val periode: MånedsperiodeJson,
    val fribeløpForEps: Double,
    val merknader: List<PersistertMerknad.Beregning> = emptyList(),
) {
    fun toMånedsberegning(): BeregningForMåned {
        return BeregningForMåned(
            måned = periode.toMånedsperiode(),
            sats = sats,
            fradrag = fradrag.map { it.toFradragForMåned() },
            fribeløpForEps = fribeløpForEps,
            merknader = Merknader.Beregningsmerknad(merknader.map { it.toDomain() }.toMutableList()),
            sumYtelse = sumYtelse,
            sumFradrag = sumFradrag,
            satsbeløp = satsbeløp,
        )
    }
}

/** Database-representasjon til serialisering */
internal fun Månedsberegning.toJson(): PersistertMånedsberegning {
    return PersistertMånedsberegning(
        sumYtelse = getSumYtelse(),
        sumFradrag = getSumFradrag(),
        benyttetGrunnbeløp = getBenyttetGrunnbeløp(),
        sats = getSats(),
        satsbeløp = getSatsbeløp(),
        fradrag = getFradrag().map { it.toJson() },
        periode = måned.toJson(),
        fribeløpForEps = getFribeløpForEps(),
        merknader = getMerknader().toSnapshot(),
    )
}
