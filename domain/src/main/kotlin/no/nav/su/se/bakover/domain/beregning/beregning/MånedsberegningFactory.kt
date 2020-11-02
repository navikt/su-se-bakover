package no.nav.su.se.bakover.domain.beregning.beregning

import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.beregning.Sats
import no.nav.su.se.bakover.domain.beregning.fradrag.IFradrag
import java.util.UUID

object MånedsberegningFactory {
    fun domene(
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

    fun db(
        id: UUID,
        opprettet: Tidspunkt,
        periode: Periode,
        sats: Sats,
        fradrag: List<IFradrag>
    ): IMånedsberegning = MånedsberegningDbWrapper(
        id = id,
        tidspunkt = opprettet,
        månedsberegning = domene(
            periode = periode,
            sats = sats,
            fradrag = fradrag
        )
    )
}
