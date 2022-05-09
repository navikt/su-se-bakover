package no.nav.su.se.bakover.domain.beregning

import no.nav.su.se.bakover.common.limitedUpwardsTo
import no.nav.su.se.bakover.common.periode.Måned
import no.nav.su.se.bakover.common.positiveOrZero
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragForMåned
import no.nav.su.se.bakover.domain.satser.FullSupplerendeStønadForMåned
import kotlin.math.roundToInt

object MånedsberegningFactory {
    fun ny(
        måned: Måned,
        fullSupplerendeStønadForMåned: FullSupplerendeStønadForMåned,
        fradrag: List<FradragForMåned>,
        fribeløpForEps: Double = 0.0,
    ): BeregningForMåned {

        val satsbeløp: Double = fullSupplerendeStønadForMåned.satsForMånedAsDouble
        val sumFradrag = fradrag.sumOf { it.månedsbeløp }.limitedUpwardsTo(satsbeløp)
        val sumYtelse: Int = (satsbeløp - sumFradrag)
            .positiveOrZero()
            .roundToInt()

        return BeregningForMåned(
            måned = måned,
            fullSupplerendeStønadForMåned = fullSupplerendeStønadForMåned,
            fradrag = fradrag,
            fribeløpForEps = fribeløpForEps,
            sumYtelse = sumYtelse,
            sumFradrag = sumFradrag,
        )
    }
}
