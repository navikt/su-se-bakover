package no.nav.su.se.bakover.domain.beregning

import no.nav.su.se.bakover.common.periode.Månedsperiode
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradrag

object MånedsberegningFactory {
    fun ny(
        måned: Månedsperiode,
        sats: Sats,
        fradrag: List<Fradrag>,
        fribeløpForEps: Double = 0.0,
    ): Månedsberegning {
        return PeriodisertBeregning(
            måned = måned,
            sats = sats,
            fradrag = fradrag,
            fribeløpForEps = fribeløpForEps
        )
    }
}
