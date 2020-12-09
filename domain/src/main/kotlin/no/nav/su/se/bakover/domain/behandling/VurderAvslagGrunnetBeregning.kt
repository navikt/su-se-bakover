package no.nav.su.se.bakover.domain.behandling

import no.nav.su.se.bakover.domain.behandling.avslag.Avslagsgrunn
import no.nav.su.se.bakover.domain.beregning.Beregning

object VurderAvslagGrunnetBeregning {
    fun vurderAvslagGrunnetBeregning(
        beregning: Beregning?
    ): AvslagGrunnetBeregning = if (beregning == null) AvslagGrunnetBeregning.Nei else when {
        beregning.getSumYtelse() <= 0 -> AvslagGrunnetBeregning.Ja(Avslagsgrunn.FOR_HØY_INNTEKT)
        beregning.getSumYtelseErUnderMinstebeløp() -> AvslagGrunnetBeregning.Ja(Avslagsgrunn.SU_UNDER_MINSTEGRENSE)
        else -> AvslagGrunnetBeregning.Nei
    }

    private fun sumYtelseErUnderMinstebeløp() {
    }
}

sealed class AvslagGrunnetBeregning {
    data class Ja(val avslagsgrunn: Avslagsgrunn) : AvslagGrunnetBeregning()
    object Nei : AvslagGrunnetBeregning()
}
