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
        return Beregning(
            id = UUID.randomUUID(),
            opprettet = Tidspunkt.now(),
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
    ): Beregning = Beregning(
        id = id,
        opprettet = opprettet,
        periode = periode,
        sats = sats,
        fradrag = fradrag
    )
}
