package satser.domain.minsteårligytelseforuføretrygdede

import arrow.core.NonEmptyList
import no.nav.su.se.bakover.common.domain.Faktor
import no.nav.su.se.bakover.common.domain.Knekkpunkt
import no.nav.su.se.bakover.common.domain.Knekkpunkt.Companion.compareTo
import no.nav.su.se.bakover.common.domain.RåSats
import no.nav.su.se.bakover.common.domain.RåSatser
import no.nav.su.se.bakover.common.domain.periodisert
import no.nav.su.se.bakover.common.extensions.erSortertOgUtenDuplikater
import no.nav.su.se.bakover.common.extensions.toNonEmptyList
import no.nav.su.se.bakover.common.tid.periode.Måned
import no.nav.su.se.bakover.common.tid.periode.erSammenhengendeSortertOgUtenDuplikater
import satser.domain.Satskategori
import java.time.LocalDate

/**
 * https://www.nav.no/no/nav-og-samfunn/kontakt-nav/oversikt-over-satser/minste-arlig-ytelse-for-uforetrygdede_kap
 * Merk at faktoren har endret seg siden loven trådte i kraft: https://lovdata.no/dokument/NL/lov/1997-02-28-19/KAPITTEL_5-9#%C2%A712-7
 * SU alder trådte ikke i kraft før 01.01.2006 og da var det 1.8 (TODO jah: sjekk med Ranveig) som var faktoren (for både ordinær og lav sats).
 * SU ufør flyktning trådte ikke i kraft før 01.01.2021 og da var det 2.28 og 2.48 som var faktoren for hhv. ordinær og høy sats.
 *
 * Merk at en ikke trenger satser før 01.01.2021 siden det er først da ufør flyktning trådte i kraft.
 *
 * Dersom det er testbehov for å ha flere verdier på samme dato, så man overskrive.
 * Dersom det hentes fra databasen, og en ønsker en append-only tabell, kan man legge til en opprettet timestamp i tabellen og la selecten velge den nyeste av de med lik dato f.eks.
 */
data class MinsteÅrligYtelseForUføretrygdedeFactory(
    private val ordinær: Map<Måned, MinsteÅrligYtelseForUføretrygdedeForMåned>,
    private val høy: Map<Måned, MinsteÅrligYtelseForUføretrygdedeForMåned>,
    val knekkpunkt: Knekkpunkt,
    val tidligsteTilgjengeligeMåned: Måned,
) {
    init {
        require(ordinær.keys == høy.keys)
        require(ordinær.values.all { it.ikrafttredelse <= knekkpunkt })
        require(høy.values.all { it.ikrafttredelse <= knekkpunkt })
        require(ordinær.isNotEmpty())
        require(ordinær.erSammenhengendeSortertOgUtenDuplikater())
        require(ordinær.keys.first() == tidligsteTilgjengeligeMåned)
    }

    private val sisteMånedMedEndring = ordinær.keys.last()

    companion object {

        fun createFromFaktorer(
            ordinær: NonEmptyList<MinsteÅrligYtelseForUføretrygdedeEndring>,
            høy: NonEmptyList<MinsteÅrligYtelseForUføretrygdedeEndring>,
            knekkpunkt: Knekkpunkt,
            tidligsteTilgjengeligeMåned: Måned,
        ): MinsteÅrligYtelseForUføretrygdedeFactory {
            ordinær.map { it.ikrafttredelse }.let {
                require(it.erSortertOgUtenDuplikater()) {
                    "Minste årlig ytelse for uføretrygdede (ordinær): Ikrafttredelse må være i stigende rekkefølge og uten duplikater, men var: $it"
                }
            }
            høy.map { it.ikrafttredelse }.let {
                require(it.erSortertOgUtenDuplikater()) {
                    "Minste årlig ytelse for uføretrygdede (høy): Ikrafttredelse må være i stigende rekkefølge og uten duplikater, men var: $it"
                }
            }
            ordinær.map { it.virkningstidspunkt }.let {
                require(it.erSortertOgUtenDuplikater()) {
                    "Minste årlig ytelse for uføretrygdede (ordinær): Virkningstidspunkt må være i stigende rekkefølge og uten duplikater, men var: $it"
                }
            }
            høy.map { it.virkningstidspunkt }.let {
                require(it.erSortertOgUtenDuplikater()) {
                    "Minste årlig ytelse for uføretrygdede (høy): Virkningstidspunkt må være i stigende rekkefølge og uten duplikater, men var: $it"
                }
            }
            return MinsteÅrligYtelseForUføretrygdedeFactory(
                ordinær = ordinær.periodiserIftVirkningstidspunkt(
                    knekkpunkt,
                    tidligsteTilgjengeligeMåned,
                    Satskategori.ORDINÆR,
                ),
                høy = høy.periodiserIftVirkningstidspunkt(knekkpunkt, tidligsteTilgjengeligeMåned, Satskategori.HØY),
                knekkpunkt = knekkpunkt,
                tidligsteTilgjengeligeMåned = tidligsteTilgjengeligeMåned,
            )
        }

        private fun NonEmptyList<MinsteÅrligYtelseForUføretrygdedeEndring>.periodiserIftVirkningstidspunkt(
            knekkpunkt: Knekkpunkt,
            tidligsteTilgjengeligeMåned: Måned,
            satskategori: Satskategori,
        ): Map<Måned, MinsteÅrligYtelseForUføretrygdedeForMåned> {
            return filterNot { it.ikrafttredelse > knekkpunkt }
                .map { RåSats(it.virkningstidspunkt, it) }
                .let {
                    RåSatser(
                        it.toNonEmptyList(),

                    )
                }
                .periodisert(tidligsteTilgjengeligeMåned)
                .associate { (virkningstidspunkt, måned, minsteÅrligYtelseForUføretrygdedeEndring) ->
                    måned to MinsteÅrligYtelseForUføretrygdedeForMåned(
                        faktor = minsteÅrligYtelseForUføretrygdedeEndring.faktor,
                        satsKategori = satskategori,
                        ikrafttredelse = minsteÅrligYtelseForUføretrygdedeEndring.ikrafttredelse,
                        virkningstidspunkt = virkningstidspunkt.also {
                            require(virkningstidspunkt == minsteÅrligYtelseForUføretrygdedeEndring.virkningstidspunkt)
                        },
                        måned = måned,
                    )
                }
        }
    }

    val fraOgMed: LocalDate = ordinær.keys.first().fraOgMed
    val tilOgMed: LocalDate = ordinær.keys.last().tilOgMed

    fun forMåned(måned: Måned, satsKategori: Satskategori): MinsteÅrligYtelseForUføretrygdedeForMåned {
        val satser = when (satsKategori) {
            Satskategori.ORDINÆR -> ordinær
            Satskategori.HØY -> høy
        }
        return satser[måned]
            ?: if (måned > sisteMånedMedEndring) {
                satser[sisteMånedMedEndring]!!.copy(
                    måned = måned,
                )
            } else {
                throw IllegalArgumentException(
                    "Har ikke data for etterspurt måned: $måned. Vi har bare data fra og med måned: ${satser.keys.first()}",
                )
            }
    }

    override fun equals(other: Any?): Boolean {
        return other is MinsteÅrligYtelseForUføretrygdedeFactory &&
            other.ordinær == ordinær &&
            other.høy == høy
    }

    override fun hashCode(): Int {
        var result = ordinær.hashCode()
        result = 31 * result + høy.hashCode()
        return result
    }

    data class MinsteÅrligYtelseForUføretrygdedeEndring(
        /** angir datoen endringen skal virke fra og med etter den har trådt i kraft */
        val virkningstidspunkt: LocalDate,
        /** angir datoen endringen trer i kraft */
        val ikrafttredelse: LocalDate,
        /** Har vært en faktor på 2.28 for ordinær og 2.48 for høy siden 2015 (tar høyde for at de kan endre seg i utakt) */
        val faktor: Faktor,
    )
}
