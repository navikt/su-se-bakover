package no.nav.su.se.bakover.domain

import no.nav.su.se.bakover.common.periode.Periode
import java.time.LocalDate
import java.time.Month

/**
 * https://www.nav.no/no/nav-og-samfunn/kontakt-nav/oversikt-over-satser/minste-pensjonsniva_kap
 */
enum class Minstepensjonsnivå {
    // Dette er den eneste vi trenger p.t., men det finnes også lav og høy
    Ordinær;

    private val datoToMinstepensjonsnivå: Map<LocalDate, Pensjonsnivåverdier> = mapOf(
        LocalDate.of(2019, Month.SEPTEMBER, 1) to Pensjonsnivåverdier(ordinær = 181908),
        LocalDate.of(2020, Month.MAY, 1) to Pensjonsnivåverdier(ordinær = 183587),
    )

    fun forDato(dato: LocalDate): Int = datoToMinstepensjonsnivå.entries
        .sortedByDescending { it.key }
        .first { dato.isAfter(it.key) || dato.isEqual(it.key) }.value.get(this)

    fun periodiser(periode: Periode): Map<Periode, Double> = periode.tilMånedsperioder()
        .map { it to minstepensjonsnivåSomMånedsbeløp(it.tilOgMed) }
        .toMap()

    private fun minstepensjonsnivåSomMånedsbeløp(dato: LocalDate) = this.forDato(dato) / 12.0

    private inner class Pensjonsnivåverdier(val ordinær: Int) {
        fun get(nivå: Minstepensjonsnivå) =
            when (nivå) {
                Ordinær -> ordinær
            }
    }
}
