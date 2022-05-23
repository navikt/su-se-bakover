package no.nav.su.se.bakover.domain.satser

import no.nav.su.se.bakover.common.periode.Måned
import java.time.LocalDate
import java.time.Month

/**
 * Fra [lov om supplerende stønad](https://lovdata.no/dokument/NL/lov/2005-04-29-21):
 * - Med garantipensjonsnivå meiner ein i lova her satsene etter [folketrygdlova § 20-9](https://lovdata.no/dokument/NL/lov/1997-02-28-19/KAPITTEL_7-2#%C2%A720-9).
 * - Garantipensjonen fastsettes med en ordinær og en høy sats som gjelder ved 67 år for ugradert pensjon med full trygdetid.
 * - Satser: https://www.nav.no/no/nav-og-samfunn/kontakt-nav/oversikt-over-satser/garantipensjon_kap
 *
 * TODO jah: Gjør denne dynamisk i senere PR
 */
enum class Garantipensjonsnivå {
    Ordinær;

    private val datoToGarantipensjonsnivå: Map<LocalDate, Pensjonsnivåverdier> = mapOf(
        LocalDate.of(2019, Month.MAY, 1) to Pensjonsnivåverdier(ordinær = 176099),
        LocalDate.of(2020, Month.MAY, 1) to Pensjonsnivåverdier(ordinær = 177724),
        LocalDate.of(2021, Month.MAY, 1) to Pensjonsnivåverdier(ordinær = 187252),
        LocalDate.of(2022, Month.MAY, 1) to Pensjonsnivåverdier(ordinær = 193862),
    )

    fun forDato(dato: LocalDate): Int = datoToGarantipensjonsnivå.entries
        .sortedByDescending { it.key }
        .first { dato.isAfter(it.key) || dato.isEqual(it.key) }.value.get(this)

    fun forMåned(måned: Måned) = this.forDato(måned.fraOgMed) / 12.0

    private inner class Pensjonsnivåverdier(val ordinær: Int) {
        fun get(nivå: Garantipensjonsnivå) =
            when (nivå) {
                Ordinær -> ordinær
            }
    }
}
