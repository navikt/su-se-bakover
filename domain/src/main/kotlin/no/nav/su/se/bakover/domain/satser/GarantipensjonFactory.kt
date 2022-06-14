package no.nav.su.se.bakover.domain.satser

import no.nav.su.se.bakover.common.erSortertOgUtenDuplikater
import no.nav.su.se.bakover.common.periode.Måned
import no.nav.su.se.bakover.common.periode.periode
import no.nav.su.se.bakover.common.periode.periodisert
import java.time.LocalDate

/**
 * Fra [lov om supplerende stønad](https://lovdata.no/dokument/NL/lov/2005-04-29-21):
 * - Med garantipensjonsnivå meiner ein i lova her satsene etter [folketrygdlova § 20-9](https://lovdata.no/dokument/NL/lov/1997-02-28-19/KAPITTEL_7-2#%C2%A720-9).
 * - Garantipensjonen fastsettes med en ordinær og en høy sats som gjelder ved 67 år for ugradert pensjon med full trygdetid.
 * - Satser: https://www.nav.no/no/nav-og-samfunn/kontakt-nav/oversikt-over-satser/garantipensjon_kap
 */
data class GarantipensjonFactory(
    val ordinær: Map<Måned, GarantipensjonForMåned>,
    val høy: Map<Måned, GarantipensjonForMåned>,
) {
    companion object {

        fun createFromSatser(
            ordinær: List<Garantipensjonsendring>,
            høy: List<Garantipensjonsendring>,
            påDato: LocalDate,
        ): GarantipensjonFactory {
            val ikrafttredelseMessage: () -> String = {
                "Ikrafttredelse for minste årlig ytelse for uføretrygdede må være i stigende rekkefølge og uten duplikater, men var: ${ordinær.map { it.virkningstidspunkt }}"
            }
            require(ordinær.map { it.ikrafttredelse }.erSortertOgUtenDuplikater(), ikrafttredelseMessage)
            require(høy.map { it.ikrafttredelse }.erSortertOgUtenDuplikater(), ikrafttredelseMessage)

            val virkningstidspunktMessage: () -> String = {
                "Virkningstidspunkt for minste årlig ytelse for uføretrygdede må være i stigende rekkefølge og uten duplikater, men var: ${ordinær.map { it.virkningstidspunkt }}"
            }
            require(ordinær.map { it.virkningstidspunkt }.erSortertOgUtenDuplikater(), virkningstidspunktMessage)
            require(høy.map { it.virkningstidspunkt }.erSortertOgUtenDuplikater(), virkningstidspunktMessage)

            return GarantipensjonFactory(
                ordinær = ordinær.periodiserIftVirkningstidspunkt(påDato, Satskategori.ORDINÆR),
                høy = høy.periodiserIftVirkningstidspunkt(påDato, Satskategori.HØY),
            )
        }

        private fun List<Garantipensjonsendring>.periodiserIftVirkningstidspunkt(
            senesteIkrafttredelse: LocalDate,
            satskategori: Satskategori,
        ): Map<Måned, GarantipensjonForMåned> {
            return filterNot { it.ikrafttredelse > senesteIkrafttredelse }
                .map { it.virkningstidspunkt to it }
                .periodisert()
                .associate { (virkningstidspunkt, måned, garantipensjonsendring) ->
                    måned to GarantipensjonForMåned(
                        måned = måned,
                        garantipensjonPerÅr = garantipensjonsendring.verdi,
                        ikrafttredelse = garantipensjonsendring.ikrafttredelse,
                        virkningstidspunkt = virkningstidspunkt,
                        satsKategori = satskategori,
                    )
                }
        }
    }

    fun forMåned(måned: Måned, satsKategori: Satskategori): GarantipensjonForMåned {
        val satser = when (satsKategori) {
            Satskategori.ORDINÆR -> ordinær
            Satskategori.HØY -> høy
        }
        return satser[måned]
            ?: throw IllegalStateException("Har ikke garantipensjon for måned: $måned. Vi har bare data for perioden: ${satser.periode()}")
    }

    data class Garantipensjonsendring(
        /** angir datoen endringen skal virke fra og med etter den har trådt i kraft */
        val virkningstidspunkt: LocalDate,
        /** angir datoen endringen trer i kraft */
        val ikrafttredelse: LocalDate,
        /** garantipensjonssatsen */
        val verdi: Int,
    )
}
