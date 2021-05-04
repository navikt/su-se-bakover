package no.nav.su.se.bakover.domain.beregning

import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradrag

object MånedsberegningFactory {
    fun ny(
        periode: Periode,
        sats: Sats,
        fradrag: List<Fradrag>,
        fribeløpForEps: Double? = null,
    ): Månedsberegning {
        return PeriodisertBeregning(
            periode = periode,
            sats = sats,
            fradrag = fradrag,
            fribeløpForEps = fribeløpForEps
        )
    }
}
