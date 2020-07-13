package no.nav.su.se.bakover.domain

import java.math.BigDecimal
import java.math.RoundingMode.HALF_UP
import java.time.LocalDate

class Beregning(private val beregningsdatoStart: LocalDate) {

    init {
        require(beregningsdatoStart.dayOfMonth == 1) {
            "Beregninger gjøres fra den første i måneden. Dato var=$beregningsdatoStart"
        }
    }

    private val periode: Int = 12

    fun beregn(): List<Int> {
        return (0 until periode).map {
            beregnMåned(beregningsdatoStart.plusMonths(it.toLong()))
        }
    }

    fun beregnMåned(dato: LocalDate): Int {
        // TODO: Finn ut om vi skal bruke forskjellig grunnbeløp "per måned" der det gjelder.
        // Er 10% regelen relevant?
        // Skal vi ta med grunnbeløp og dato i visninga
        return BigDecimal(Sats.HØY.fraDato(dato)).divide(BigDecimal(periode), 0, HALF_UP).toInt()
    }

    enum class Sats(val grunnbeløp: Grunnbeløp) {
        LAV(Grunnbeløp.`2,28G`),
        HØY(Grunnbeløp.`2,48G`);

        fun fraDato(dato: LocalDate) = grunnbeløp.fraDato(dato)
    }
}
