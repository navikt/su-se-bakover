package no.nav.su.se.bakover.domain.beregning.beregning

import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.beregning.Sats
import no.nav.su.se.bakover.domain.beregning.fradrag.IFradrag

object MånedsberegningFactory {
    fun ny(
        periode: Periode,
        sats: Sats,
        fradrag: List<IFradrag>
    ): IMånedsberegning {
        return Månedsberegning(
            periode = periode,
            sats = sats,
            fradrag = fradrag
        )
    }
}
