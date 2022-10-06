package no.nav.su.se.bakover.database.beregning

import no.nav.su.se.bakover.common.infrastructure.web.periode.MånedJson
import no.nav.su.se.bakover.common.infrastructure.web.periode.MånedJson.Companion.toJson
import no.nav.su.se.bakover.domain.Sakstype
import no.nav.su.se.bakover.domain.beregning.BeregningForMåned
import no.nav.su.se.bakover.domain.beregning.Merknader
import no.nav.su.se.bakover.domain.beregning.Månedsberegning
import no.nav.su.se.bakover.domain.satser.SatsFactory
import no.nav.su.se.bakover.domain.satser.Satskategori

internal data class PersistertMånedsberegning(
    val sumYtelse: Int,
    val sumFradrag: Double,
    val benyttetGrunnbeløp: Int?, // bare relevant for uføre
    val sats: Satskategori,
    val satsbeløp: Double,
    val fradrag: List<PersistertFradrag>,
    val periode: MånedJson,
    val fribeløpForEps: Double,
    val merknader: List<PersistertMerknad.Beregning> = emptyList(),
) {
    fun toMånedsberegning(satsFactory: SatsFactory, sakstype: Sakstype): BeregningForMåned {
        val måned = periode.tilMåned()
        return BeregningForMåned(
            måned = måned,
            fullSupplerendeStønadForMåned = when (sakstype) {
                Sakstype.ALDER -> {
                    satsFactory.forSatskategoriAlder(
                        måned = måned,
                        satskategori = sats,
                    ).also {
                        assert(satsbeløp == it.satsForMånedAsDouble)
                        assert(sats == it.satskategori)
                    }
                }
                Sakstype.UFØRE -> {
                    satsFactory.forSatskategoriUføre(
                        måned = måned,
                        satskategori = sats,
                    ).also {
                        assert(benyttetGrunnbeløp == it.grunnbeløp.grunnbeløpPerÅr) {
                            "Hentet benyttetGrunnbeløp: $benyttetGrunnbeløp fra databasen, mens den utleda verdien for grunnbeløp var: ${it.grunnbeløp.grunnbeløpPerÅr}"
                        }
                        assert(satsbeløp == it.satsForMånedAsDouble)
                        assert(sats == it.satskategori)
                    }
                }
            },
            fradrag = fradrag.map { it.toFradragForMåned() },
            fribeløpForEps = fribeløpForEps,
            merknader = Merknader.Beregningsmerknad(merknader.map { it.toDomain() }.toMutableList()),
            sumYtelse = sumYtelse,
            sumFradrag = sumFradrag,
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
