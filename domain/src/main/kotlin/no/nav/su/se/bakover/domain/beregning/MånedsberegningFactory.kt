package no.nav.su.se.bakover.domain.beregning

import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradrag
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragFactory

object MånedsberegningFactory {
    fun ny(
        periode: Periode,
        sats: Sats,
        fradrag: List<Fradrag>,
        fribeløpForEps: Double = 0.0,
    ): Månedsberegning {
        return PeriodisertBeregning(
            periode = periode,
            sats = sats,
            fradrag = fradrag.flatMap { FradragFactory.periodiserInternal(it) },
            fribeløpForEps = fribeløpForEps,
        )
    }
}
