package no.nav.su.se.bakover.domain

import no.nav.su.se.bakover.common.ApplicationConfig
import no.nav.su.se.bakover.common.log
import no.nav.su.se.bakover.common.periode.Periode
import java.time.LocalDate
import java.time.Month

/**
 * https://www.nav.no/no/nav-og-samfunn/kontakt-nav/oversikt-over-satser/garantipensjon_kap
 */
enum class Garantipensjonsnivå {
    Ordinær;

    private val datoToGarantipensjonsnivå: Map<LocalDate, Pensjonsnivåverdier> = listOfNotNull(
        LocalDate.of(2019, Month.MAY, 1) to Pensjonsnivåverdier(ordinær = 176099),
        LocalDate.of(2020, Month.MAY, 1) to Pensjonsnivåverdier(ordinær = 177724),
        if (ApplicationConfig.isNotProd()) {
            log.warn("Inkluderer fiktivt garantipensjonsnivå for 2021. Skal ikke dukke opp i prod!")
            LocalDate.of(2021, Month.MAY, 1) to Pensjonsnivåverdier(ordinær = 179123)
        } else null,
    ).toMap()

    fun forDato(dato: LocalDate): Int = datoToGarantipensjonsnivå.entries
        .sortedByDescending { it.key }
        .first { dato.isAfter(it.key) || dato.isEqual(it.key) }.value.get(this)

    fun periodiser(periode: Periode): Map<Periode, Double> = periode.tilMånedsperioder()
        .map { it to garantipensjonsnivåSomMånedsbeløp(it.tilOgMed) }
        .toMap()

    private fun garantipensjonsnivåSomMånedsbeløp(dato: LocalDate) = this.forDato(dato) / 12.0

    private inner class Pensjonsnivåverdier(val ordinær: Int) {
        fun get(nivå: Garantipensjonsnivå) =
            when (nivå) {
                Ordinær -> ordinær
            }
    }
}
