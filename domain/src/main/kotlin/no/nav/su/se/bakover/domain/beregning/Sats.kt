package no.nav.su.se.bakover.domain.beregning

import no.nav.su.se.bakover.domain.Grunnbeløp
import java.math.BigDecimal
import java.time.LocalDate

enum class Sats(val grunnbeløp: Grunnbeløp) {
    ORDINÆR(Grunnbeløp.`2,28G`),
    HØY(Grunnbeløp.`2,48G`);

    fun fraDato(dato: LocalDate): Double = grunnbeløp.fraDato(dato)
    fun fraDatoAsInt(dato: LocalDate): Int = BigDecimal.valueOf(grunnbeløp.fraDato(dato)).divide(BigDecimal.ONE, 0, java.math.RoundingMode.HALF_UP).toInt()
}
