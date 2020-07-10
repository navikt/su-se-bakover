package no.nav.su.se.bakover.domain

import java.time.LocalDate
import java.time.Month

class Grunnbeløp private constructor(private val sats: Double) {
    private val datoToBeløp: Map<LocalDate, Int> = mapOf(
        LocalDate.of(2017, Month.MAY, 1) to 93634,
        LocalDate.of(2018, Month.MAY, 1) to 96883,
        LocalDate.of(2019, Month.MAY, 1) to 99858
    )

    fun fraDato(dato: LocalDate): Double = datoToBeløp.entries
        .sortedByDescending { it.key }
        .first {
            dato.isAfter(it.key) || dato.isEqual(it.key)
        }.value * sats

    companion object {
        val lav = Grunnbeløp(2.28)
        val høy = Grunnbeløp(2.48)
    }
}
