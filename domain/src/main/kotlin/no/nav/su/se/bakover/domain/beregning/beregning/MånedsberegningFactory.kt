package no.nav.su.se.bakover.domain.beregning.beregning

import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.beregning.Sats
import no.nav.su.se.bakover.domain.beregning.fradrag.AbstractFradrag
import java.util.UUID

object MånedsberegningFactory {
    fun domene(
        periode: Periode,
        sats: Sats,
        fradrag: List<AbstractFradrag>
    ): AbstractMånedsberegning {
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
        fradrag: List<AbstractFradrag>
    ): AbstractMånedsberegning = MånedsberegningDbWrapper(
        id = id,
        tidspunkt = opprettet,
        månedsberegning = domene(
            periode = periode,
            sats = sats,
            fradrag = fradrag
        )
    )
}
