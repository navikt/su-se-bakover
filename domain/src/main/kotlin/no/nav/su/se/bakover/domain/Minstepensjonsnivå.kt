package no.nav.su.se.bakover.domain

import java.time.LocalDate
import java.time.Month

enum class Minstepensjonsnivå {
    // Dette er den eneste vi trenger p.t., men det finnes også lav og høy
    Ordinær;

    fun forDato(dato: LocalDate): Int = datoToBeløp.entries
        .sortedByDescending { it.key }
        .first { dato.isAfter(it.key) || dato.isEqual(it.key) }
        .value
        .get(this)

    private val datoToBeløp: Map<LocalDate, Pensjonsnivåverdier> = mapOf(
        LocalDate.of(2019, Month.SEPTEMBER, 1) to Pensjonsnivåverdier(ordinær = 181908),
        LocalDate.of(2020, Month.MAY, 1) to Pensjonsnivåverdier(ordinær = 183587),
    )

    private inner class Pensjonsnivåverdier(val ordinær: Int) {
        fun get(nivå: Minstepensjonsnivå) =
            when (nivå) {
                Ordinær -> ordinær
            }
    }
}
