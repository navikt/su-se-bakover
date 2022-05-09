package no.nav.su.se.bakover.domain.beregning

import no.nav.su.se.bakover.common.limitedUpwardsTo
import no.nav.su.se.bakover.common.periode.Månedsperiode
import no.nav.su.se.bakover.common.positiveOrZero
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragForMåned
import kotlin.math.roundToInt

object MånedsberegningFactory {
    fun ny(
        måned: Månedsperiode,
        sats: Sats,
        fradrag: List<FradragForMåned>,
        fribeløpForEps: Double = 0.0,
    ): BeregningForMåned {

        val satsbeløp: Double = sats.periodiser(måned).getValue(måned)
        val sumFradrag = fradrag.sumOf { it.månedsbeløp }.limitedUpwardsTo(satsbeløp)
        val sumYtelse: Int = (satsbeløp - sumFradrag)
            .positiveOrZero()
            .roundToInt()

        return BeregningForMåned(
            måned = måned,
            sats = sats,
            fradrag = fradrag,
            fribeløpForEps = fribeløpForEps,
            sumYtelse = sumYtelse,
            sumFradrag = sumFradrag,
            satsbeløp = satsbeløp,
        )
    }
}
