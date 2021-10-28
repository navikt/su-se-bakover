package no.nav.su.se.bakover.domain.behandling

import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.beregning.utenSosialstønad

object VurderAvslagGrunnetBeregning {
    fun vurderAvslagGrunnetBeregning(
        beregning: Beregning?,
    ): AvslagGrunnetBeregning = if (beregning == null) AvslagGrunnetBeregning.Nei else {
        val beregningUtenSosialstønad = beregning.utenSosialstønad()

        when {
            beregningUtenSosialstønad.getMånedsberegninger().any { it.erSumYtelseUnderMinstebeløp() } -> AvslagGrunnetBeregning.Ja(AvslagGrunnetBeregning.Grunn.SU_UNDER_MINSTEGRENSE)
            beregningUtenSosialstønad.getMånedsberegninger().any { it.getSumYtelse() <= 0 } -> AvslagGrunnetBeregning.Ja(AvslagGrunnetBeregning.Grunn.FOR_HØY_INNTEKT)
            else -> AvslagGrunnetBeregning.Nei
        }
    }
}

sealed class AvslagGrunnetBeregning {
    data class Ja(val grunn: Grunn) : AvslagGrunnetBeregning()
    object Nei : AvslagGrunnetBeregning()

    enum class Grunn {
        FOR_HØY_INNTEKT,
        SU_UNDER_MINSTEGRENSE
    }
}
