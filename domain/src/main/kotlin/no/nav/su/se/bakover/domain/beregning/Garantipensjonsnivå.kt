package no.nav.su.se.bakover.domain.beregning

import no.nav.su.se.bakover.common.periode.Periode
import java.time.LocalDate
import java.time.Month

/**
 * https://www.nav.no/no/nav-og-samfunn/kontakt-nav/oversikt-over-satser/garantipensjon_kap
 */
enum class Garantipensjonsnivå {
    Ordinær;

    private val datoToGarantipensjonsnivå: Map<LocalDate, Pensjonsnivåverdier> = mapOf(
        LocalDate.of(2019, Month.MAY, 1) to Pensjonsnivåverdier(ordinær = 176099),
        LocalDate.of(2020, Month.MAY, 1) to Pensjonsnivåverdier(ordinær = 177724),
        LocalDate.of(2021, Month.MAY, 1) to Pensjonsnivåverdier(ordinær = 186263),
    )

    fun forDato(dato: LocalDate): Int = datoToGarantipensjonsnivå.entries
        .sortedByDescending { it.key }
        .first { dato.isAfter(it.key) || dato.isEqual(it.key) }.value.get(this)

    fun periodiser(periode: Periode): Map<Periode, Double> {
        return periode
            .tilMånedsperioder()
            .associateWith { garantipensjonsnivåSomMånedsbeløp(it.tilOgMed) }
    }

    private fun garantipensjonsnivåSomMånedsbeløp(dato: LocalDate) = this.forDato(dato) / 12.0

    private inner class Pensjonsnivåverdier(val ordinær: Int) {
        fun get(nivå: Garantipensjonsnivå) =
            when (nivå) {
                Ordinær -> ordinær
            }
    }
}
