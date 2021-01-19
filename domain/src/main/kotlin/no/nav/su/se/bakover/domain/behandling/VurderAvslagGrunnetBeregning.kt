package no.nav.su.se.bakover.domain.behandling

import no.nav.su.se.bakover.domain.behandling.avslag.Avslagsgrunn
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.beregning.Månedsberegning

object VurderAvslagGrunnetBeregning {
    fun vurderAvslagGrunnetBeregning(
        beregning: Beregning?
    ): AvslagGrunnetBeregning = if (beregning == null) AvslagGrunnetBeregning.Nei else {
        val (første, siste) = hentFørsteOgSisteMånedsberegning(beregning.getMånedsberegninger())
        when {
            første.erSumYtelseUnderMinstebeløp() -> AvslagGrunnetBeregning.Ja(Avslagsgrunn.SU_UNDER_MINSTEGRENSE)
            første.getSumYtelse() <= 0 -> AvslagGrunnetBeregning.Ja(Avslagsgrunn.FOR_HØY_INNTEKT)
            siste.erSumYtelseUnderMinstebeløp() -> AvslagGrunnetBeregning.Ja(Avslagsgrunn.SU_UNDER_MINSTEGRENSE)
            siste.getSumYtelse() <= 0 -> AvslagGrunnetBeregning.Ja(Avslagsgrunn.FOR_HØY_INNTEKT)
            else -> AvslagGrunnetBeregning.Nei
        }
    }

    private fun hentFørsteOgSisteMånedsberegning(månedsberegninger: List<Månedsberegning>): Pair<Månedsberegning, Månedsberegning> =
        månedsberegninger.first() to månedsberegninger.last()
}

sealed class AvslagGrunnetBeregning {
    data class Ja(val avslagsgrunn: Avslagsgrunn) : AvslagGrunnetBeregning()
    object Nei : AvslagGrunnetBeregning()
}
