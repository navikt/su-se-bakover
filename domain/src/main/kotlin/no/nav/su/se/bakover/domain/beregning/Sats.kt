package no.nav.su.se.bakover.domain.beregning

import no.nav.su.se.bakover.domain.Grunnbeløp
import java.time.LocalDate

enum class Sats(val grunnbeløp: Grunnbeløp) {
    LAV(Grunnbeløp.`2,28G`),
    HØY(Grunnbeløp.`2,48G`);

    fun fraDato(dato: LocalDate) = grunnbeløp.fraDato(dato)
}
