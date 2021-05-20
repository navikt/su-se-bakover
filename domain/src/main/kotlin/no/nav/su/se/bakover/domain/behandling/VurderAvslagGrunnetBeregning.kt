package no.nav.su.se.bakover.domain.behandling

import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.beregning.Månedsberegning

object VurderAvslagGrunnetBeregning {
    fun vurderAvslagGrunnetBeregning(
        beregning: Beregning?,
    ): AvslagGrunnetBeregning = if (beregning == null) AvslagGrunnetBeregning.Nei else {
        val (første, siste) = hentFørsteOgSisteMånedsberegning(beregning.getMånedsberegninger())
        when {
            første.erSumYtelseUnderMinstebeløp() -> AvslagGrunnetBeregning.Ja(AvslagGrunnetBeregning.Grunn.SU_UNDER_MINSTEGRENSE)
            første.getSumYtelse() <= 0 -> AvslagGrunnetBeregning.Ja(AvslagGrunnetBeregning.Grunn.FOR_HØY_INNTEKT)
            siste.erSumYtelseUnderMinstebeløp() -> AvslagGrunnetBeregning.Ja(AvslagGrunnetBeregning.Grunn.SU_UNDER_MINSTEGRENSE)
            siste.getSumYtelse() <= 0 -> AvslagGrunnetBeregning.Ja(AvslagGrunnetBeregning.Grunn.FOR_HØY_INNTEKT)
            else -> AvslagGrunnetBeregning.Nei
        }
    }

    fun hentAvslagsgrunnForBeregning(beregning: Beregning?): AvslagGrunnetBeregning.Grunn? {
        return when (val v = vurderAvslagGrunnetBeregning(beregning)) {
            is AvslagGrunnetBeregning.Ja -> v.avslagsgrunn
            is AvslagGrunnetBeregning.Nei -> null
        }
    }

    private fun hentFørsteOgSisteMånedsberegning(månedsberegninger: List<Månedsberegning>): Pair<Månedsberegning, Månedsberegning> =
        månedsberegninger.first() to månedsberegninger.last()
}

sealed class AvslagGrunnetBeregning {
    data class Ja(val avslagsgrunn: Grunn) : AvslagGrunnetBeregning()
    object Nei : AvslagGrunnetBeregning()

    enum class Grunn {
        FOR_HØY_INNTEKT,
        SU_UNDER_MINSTEGRENSE
    }
}
