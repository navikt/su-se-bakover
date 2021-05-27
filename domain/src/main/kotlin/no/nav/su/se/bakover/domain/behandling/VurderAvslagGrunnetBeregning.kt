package no.nav.su.se.bakover.domain.behandling

import no.nav.su.se.bakover.domain.beregning.Beregning

object VurderAvslagGrunnetBeregning {
    fun vurderAvslagGrunnetBeregning(
        beregning: Beregning?,
    ): AvslagGrunnetBeregning = if (beregning == null) AvslagGrunnetBeregning.Nei else {
        when {
            beregning.getMånedsberegninger().any { it.erSumYtelseUnderMinstebeløp() } -> AvslagGrunnetBeregning.Ja(AvslagGrunnetBeregning.Grunn.SU_UNDER_MINSTEGRENSE)
            beregning.getMånedsberegninger().any { it.getSumYtelse() <= 0 } -> AvslagGrunnetBeregning.Ja(AvslagGrunnetBeregning.Grunn.FOR_HØY_INNTEKT)
            else -> AvslagGrunnetBeregning.Nei
        }
    }

    fun hentAvslagsgrunnForBeregning(beregning: Beregning?): AvslagGrunnetBeregning.Grunn? {
        return when (val v = vurderAvslagGrunnetBeregning(beregning)) {
            is AvslagGrunnetBeregning.Ja -> v.grunn
            is AvslagGrunnetBeregning.Nei -> null
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
