package no.nav.su.se.bakover.domain.beregning

import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradrag
import java.util.UUID

object BeregningFactory {
    fun ny(
        periode: Periode,
        sats: Sats,
        fradrag: List<Fradrag>
    ): Beregning {
        return PeriodeBeregning(
            periode = periode,
            sats = sats,
            fradrag = fradrag
        )
    }

    fun persistert(
        id: UUID,
        opprettet: Tidspunkt,
        periode: Periode,
        sats: Sats,
        fradrag: List<Fradrag>
    ): Beregning = PersistertBeregning(
        id = id,
        tidspunkt = opprettet,
        beregning = ny(
            periode = periode,
            sats = sats,
            fradrag = fradrag
        )
    )
}
