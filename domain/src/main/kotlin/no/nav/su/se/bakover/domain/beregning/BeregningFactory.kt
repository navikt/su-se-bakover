package no.nav.su.se.bakover.domain.beregning

import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.periode.reduser
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradrag
import java.time.Clock
import java.util.UUID

class BeregningFactory(val clock: Clock) {
    fun ny(
        id: UUID = UUID.randomUUID(),
        opprettet: Tidspunkt = Tidspunkt.now(clock),
        fradrag: List<Fradrag>,
        begrunnelse: String? = null,
        beregningsperioder: List<Beregningsperiode>,
    ): Beregning {
        return BeregningMedFradragBeregnetMånedsvis(
            id = id,
            opprettet = opprettet,
            periode = beregningsperioder.map { it.periode() }.reduser().single(),
            sats = beregningsperioder.first().sats(),
            fradrag = fradrag,
            fradragStrategy = beregningsperioder.first().fradragStrategy(),
            begrunnelse = begrunnelse,
            beregningsperioder = beregningsperioder,
        )
    }
}
