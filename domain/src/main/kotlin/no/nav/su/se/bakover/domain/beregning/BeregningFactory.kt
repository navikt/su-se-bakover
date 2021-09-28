package no.nav.su.se.bakover.domain.beregning

import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradrag
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragStrategy
import java.util.UUID

object BeregningFactory {
    fun ny(
        id: UUID = UUID.randomUUID(),
        opprettet: Tidspunkt = Tidspunkt.now(),
        periode: Periode,
        sats: Sats,
        fradrag: List<Fradrag>,
        fradragStrategy: FradragStrategy,
        begrunnelse: String? = null,
        månedsberegning: Månedsberegning? = null,
    ): Beregning {
        return BeregningMedVirkningstidspunkt(
            id = id,
            opprettet = opprettet,
            periode = periode,
            sats = sats,
            fradrag = fradrag,
            fradragStrategy = fradragStrategy,
            begrunnelse = begrunnelse,
            gjeldendeMånedsberegningFraTidligere = månedsberegning,
        )
    }
}
