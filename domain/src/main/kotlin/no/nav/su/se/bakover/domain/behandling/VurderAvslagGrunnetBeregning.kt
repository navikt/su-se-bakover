package no.nav.su.se.bakover.domain.behandling

import no.nav.su.se.bakover.domain.behandling.avslag.Avslagsgrunn
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.beregning.Sats

object VurderAvslagGrunnetBeregning {
    fun vurderAvslagGrunnetBeregning(
        beregning: Beregning?
    ): AvslagGrunnetBeregning = if (beregning == null) AvslagGrunnetBeregning.Nei else when {
        beregning.getSumYtelse() <= 0 -> AvslagGrunnetBeregning.Ja(Avslagsgrunn.FOR_HØY_INNTEKT)
        harMånederUnderMinstebeløp(beregning) -> AvslagGrunnetBeregning.Ja(Avslagsgrunn.SU_UNDER_MINSTEGRENSE)
        else -> AvslagGrunnetBeregning.Nei
    }

    private fun harMånederUnderMinstebeløp(beregning: Beregning): Boolean = beregning.getMånedsberegninger()
        .any { it.getSumYtelse() < Sats.toProsentAvHøy(it.getPeriode()) }
}

sealed class AvslagGrunnetBeregning {
    data class Ja(val avslagsgrunn: Avslagsgrunn) : AvslagGrunnetBeregning()
    object Nei : AvslagGrunnetBeregning()
}
