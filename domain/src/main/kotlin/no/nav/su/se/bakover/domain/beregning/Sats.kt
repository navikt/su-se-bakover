package no.nav.su.se.bakover.domain.beregning

import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.Grunnbeløp
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.math.roundToInt

enum class Sats(val grunnbeløp: Grunnbeløp) {
    ORDINÆR(Grunnbeløp.`2,28G`),
    HØY(Grunnbeløp.`2,48G`);

    private val MÅNEDER = 12L
    private val MÅNEDER_PRESISJON = 2
    private val ÅR = 1L
    private val ÅR_PRESISJON = 0

    fun årsbeløp(dato: LocalDate): Int = satsSomÅrsbeløp(dato)
    fun månedsbeløp(dato: LocalDate) = satsSomMånedsbeløp(dato)
    fun toProsentAvHøySats(periode: Periode) = periode.periodiserMåneder()
        .sumByDouble { HØY.månedsbeløp(it.fraOgMed()) * 0.02 }
        .roundToInt()

    fun periodiser(periode: Periode): Map<Periode, Double> = periode.periodiserMåneder()
        .map { it to satsSomMånedsbeløp(it.fraOgMed()) }
        .toMap()

    private fun satsSomÅrsbeløp(dato: LocalDate) = kalkuler(dato, ÅR, ÅR_PRESISJON).toInt()

    private fun satsSomMånedsbeløp(dato: LocalDate) = kalkuler(dato, MÅNEDER, MÅNEDER_PRESISJON).toDouble()

    private fun kalkuler(dato: LocalDate, divisor: Long, presisjon: Int): Number {
        return BigDecimal.valueOf(grunnbeløp.fraDato(dato))
            .divide(BigDecimal.valueOf(divisor), presisjon, java.math.RoundingMode.HALF_UP)
    }
}
