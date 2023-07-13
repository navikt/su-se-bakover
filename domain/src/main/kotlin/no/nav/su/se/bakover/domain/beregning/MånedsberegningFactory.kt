package no.nav.su.se.bakover.domain.beregning

import no.nav.su.se.bakover.common.extensions.limitedUpwardsTo
import no.nav.su.se.bakover.common.extensions.positiveOrZero
import no.nav.su.se.bakover.common.tid.periode.Måned
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradrag
import no.nav.su.se.bakover.domain.beregning.fradrag.sum
import kotlin.math.roundToInt

data object MånedsberegningFactory {
    /**
     * Beregner ytelsen for en spesifiskert måned i henhold til angitt strategi.
     *
     * @param måned måned det skal beregnes for
     * @param strategy strategien som skal benyttes for beregningen
     * @param fradrag en liste med fradrag som skal trekkes fra ytelsen. Kun fradrag som er aktuelle for [måned] tas med i beregningen.
     */
    fun ny(
        måned: Måned,
        strategy: BeregningStrategy,
        fradrag: List<Fradrag>,
    ): BeregningForMåned {
        val ytelseFørFradrag = strategy.beregn(måned)
        val fradragForMåned = strategy.beregnFradrag(måned, fradrag)
        val fribeløpForEps = strategy.beregnFribeløpEPS(måned)

        val satsbeløp: Double = ytelseFørFradrag.satsForMånedAsDouble
        val sumFradrag = fradragForMåned.sum().limitedUpwardsTo(satsbeløp)
        val sumYtelse: Int = (satsbeløp - sumFradrag)
            .positiveOrZero()
            .roundToInt()

        return BeregningForMåned(
            måned = måned,
            fullSupplerendeStønadForMåned = ytelseFørFradrag,
            fradrag = fradragForMåned,
            fribeløpForEps = fribeløpForEps,
            sumYtelse = sumYtelse,
            sumFradrag = sumFradrag,
        )
    }
}
