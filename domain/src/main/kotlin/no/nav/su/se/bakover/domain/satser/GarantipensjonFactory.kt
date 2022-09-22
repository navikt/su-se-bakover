package no.nav.su.se.bakover.domain.satser

import no.nav.su.se.bakover.common.erSortertOgUtenDuplikater
import no.nav.su.se.bakover.common.periode.Måned
import no.nav.su.se.bakover.common.periode.erSammenhengendeSortertOgUtenDuplikater
import no.nav.su.se.bakover.common.toNonEmptyList
import no.nav.su.se.bakover.domain.satser.Knekkpunkt.Companion.compareTo
import java.time.LocalDate

/**
 * Fra [lov om supplerende stønad](https://lovdata.no/dokument/NL/lov/2005-04-29-21):
 * - Med garantipensjonsnivå meiner ein i lova her satsene etter [folketrygdlova § 20-9](https://lovdata.no/dokument/NL/lov/1997-02-28-19/KAPITTEL_7-2#%C2%A720-9).
 * - Garantipensjonen fastsettes med en ordinær og en høy sats som gjelder ved 67 år for ugradert pensjon med full trygdetid.
 * - Satser: https://www.nav.no/no/nav-og-samfunn/kontakt-nav/oversikt-over-satser/garantipensjon_kap
 *
 * @param knekkpunkt ikrafttredelsesdatoen til en gitt lov/sats. Brukes for å finne ut hvilke satser som gjaldt på en gitt dato.
 * @param tidligsteTilgjengeligeMåned Første måned denne satsen er aktuell. Som for denne satsen er 2021-01-01, men siden vi har tester som antar den gjelder før dette er den dynamisk.
 */
data class GarantipensjonFactory private constructor(
    val ordinær: Map<Måned, GarantipensjonForMåned>,
    val høy: Map<Måned, GarantipensjonForMåned>,
    val knekkpunkt: Knekkpunkt,
    val tidligsteTilgjengeligeMåned: Måned,
) {
    private val sisteMånedMedEndring = ordinær.keys.last()

    init {
        require(ordinær.keys == høy.keys) {
            "ordinær og høy må ha de samme månedene"
        }
        require(ordinær.values.all { it.ikrafttredelse <= knekkpunkt })
        require(høy.values.all { it.ikrafttredelse <= knekkpunkt })
        require(ordinær.isNotEmpty())
        require(ordinær.erSammenhengendeSortertOgUtenDuplikater())
        require(ordinær.keys.first() == tidligsteTilgjengeligeMåned)
    }

    companion object {

        fun createFromSatser(
            ordinær: List<Garantipensjonsendring>,
            høy: List<Garantipensjonsendring>,
            knekkpunkt: Knekkpunkt,
            tidligsteTilgjengeligeMåned: Måned,
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
                ordinær = ordinær.periodiserIftVirkningstidspunkt(knekkpunkt, tidligsteTilgjengeligeMåned, Satskategori.ORDINÆR),
                høy = høy.periodiserIftVirkningstidspunkt(knekkpunkt, tidligsteTilgjengeligeMåned, Satskategori.HØY),
                knekkpunkt = knekkpunkt,
                tidligsteTilgjengeligeMåned = tidligsteTilgjengeligeMåned,
            )
        }

        private fun List<Garantipensjonsendring>.periodiserIftVirkningstidspunkt(
            knekkpunkt: Knekkpunkt,
            tidligsteTilgjengeligeMåned: Måned,
            satskategori: Satskategori,
        ): Map<Måned, GarantipensjonForMåned> {
            return filterNot { it.ikrafttredelse > knekkpunkt }
                .map { RåSats(it.virkningstidspunkt, it) }
                .let {
                    RåSatser(
                        it.toNonEmptyList(),

                    )
                }
                .periodisert(tidligsteTilgjengeligeMåned)
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
            ?: (
                if (måned > sisteMånedMedEndring) {
                    satser[sisteMånedMedEndring]!!.copy(
                        måned = måned,
                    )
                } else {
                    throw IllegalArgumentException("Har ikke data for etterspurt måned: $måned. Vi har bare data fra og med måned: ${satser.keys.first()}")
                }
                )
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
