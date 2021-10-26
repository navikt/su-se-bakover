package no.nav.su.se.bakover.domain.behandling

import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.beregning.BeregningFactory
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragStrategy
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype

object VurderAvslagGrunnetBeregning {
    fun vurderAvslagGrunnetBeregning(
        beregning: Beregning?,
    ): AvslagGrunnetBeregning = if (beregning == null) AvslagGrunnetBeregning.Nei else {
        val beregningUtenSosialstønad = beregnUtenSosialstønad(beregning)

        when {
            beregningUtenSosialstønad.getMånedsberegninger().any { it.erSumYtelseUnderMinstebeløp() } -> AvslagGrunnetBeregning.Ja(AvslagGrunnetBeregning.Grunn.SU_UNDER_MINSTEGRENSE)
            beregningUtenSosialstønad.getMånedsberegninger().any { it.getSumYtelse() <= 0 } -> AvslagGrunnetBeregning.Ja(AvslagGrunnetBeregning.Grunn.FOR_HØY_INNTEKT)
            else -> AvslagGrunnetBeregning.Nei
        }
    }

    /* Sosialstønad skal ikke kunne føre til avslag eller under minste grense for utbetaling */
    private fun beregnUtenSosialstønad(beregning: Beregning): Beregning = BeregningFactory.ny(
        periode = beregning.periode,
        sats = beregning.getSats(),
        fradrag = beregning.getFradrag().filterNot { it.fradragstype == Fradragstype.Sosialstønad },
        fradragStrategy = FradragStrategy.fromName(beregning.getFradragStrategyName()),
        begrunnelse = beregning.getBegrunnelse()
    )
}

sealed class AvslagGrunnetBeregning {
    data class Ja(val grunn: Grunn) : AvslagGrunnetBeregning()
    object Nei : AvslagGrunnetBeregning()

    enum class Grunn {
        FOR_HØY_INNTEKT,
        SU_UNDER_MINSTEGRENSE
    }
}
