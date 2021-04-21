package no.nav.su.se.bakover.domain.beregning

import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.Grunnbeløp
import java.time.LocalDate
import kotlin.math.roundToInt

enum class Sats(val grunnbeløp: Grunnbeløp) {
    ORDINÆR(Grunnbeløp.`2,28G`),
    HØY(Grunnbeløp.`2,48G`);

    fun årsbeløp(dato: LocalDate): Double = satsSomÅrsbeløp(dato)

    fun månedsbeløp(dato: LocalDate): Double = satsSomMånedsbeløp(dato)

    fun månedsbeløpSomHeltall(dato: LocalDate): Int = satsSomMånedsbeløp(dato).roundToInt()

    fun periodiser(periode: Periode): Map<Periode, Double> {
        return periode.tilMånedsperioder().associateWith { satsSomMånedsbeløp(it.getFraOgMed()) }
    }

    private fun satsSomÅrsbeløp(dato: LocalDate): Double = grunnbeløp.fraDato(dato)

    private fun satsSomMånedsbeløp(dato: LocalDate): Double = grunnbeløp.fraDato(dato) / 12

    companion object {
        fun toProsentAvHøy(periode: Periode): Double = periode.tilMånedsperioder()
            .sumByDouble { HØY.månedsbeløp(it.getFraOgMed()) * 0.02 }
    }
}
