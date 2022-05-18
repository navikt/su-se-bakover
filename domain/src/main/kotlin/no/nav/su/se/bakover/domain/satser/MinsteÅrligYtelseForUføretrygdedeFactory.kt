package no.nav.su.se.bakover.domain.satser

import no.nav.su.se.bakover.common.periode.Måned
import no.nav.su.se.bakover.common.periode.erSammenhengendeSortertOgUtenDuplikater
import no.nav.su.se.bakover.common.periode.periode
import no.nav.su.se.bakover.common.periode.periodisert
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
    val ordinær: Map<Måned, MinsteÅrligYtelseForUføretrygdedeForMåned>,
    val høy: Map<Måned, MinsteÅrligYtelseForUføretrygdedeForMåned>,
) {
    companion object {
        fun createFromFaktorer(
            ordinær: List<Pair<LocalDate, Faktor>>,
            høy: List<Pair<LocalDate, Faktor>>,
        ): MinsteÅrligYtelseForUføretrygdedeFactory {

            fun mapper(satsKategori: Satskategori): Map<Måned, MinsteÅrligYtelseForUføretrygdedeForMåned> {
                val satser = when (satsKategori) {
                    Satskategori.ORDINÆR -> ordinær
                    Satskategori.HØY -> høy
                }
                return satser.periodisert().associate { (ikrafttredelse, måned, faktor) ->
                    måned to MinsteÅrligYtelseForUføretrygdedeForMåned(
                        faktor = faktor,
                        satsKategori = satsKategori,
                        ikrafttredelse = ikrafttredelse,
                        måned = måned,
                    )
                }
            }

            return MinsteÅrligYtelseForUføretrygdedeFactory(
                ordinær = mapper(Satskategori.ORDINÆR),
                høy = mapper(Satskategori.HØY),
            )
        }
    }

    init {
        assert(ordinær.isNotEmpty())
        assert(høy.isNotEmpty())
        assert(ordinær.erSammenhengendeSortertOgUtenDuplikater())

        assert(ordinær.toSortedMap() == ordinær)
        assert(høy.toSortedMap() == høy)

        assert(ordinær.keys.distinct() == ordinær.keys.toList())
        assert(høy.keys.distinct() == høy.keys.toList())

        assert(ordinær.keys.first() == høy.keys.first())
        assert(ordinær.keys.first().fraOgMed <= supplerendeStønadAlderFlyktningIkrafttredelse)
    }

    val fraOgMed: LocalDate = ordinær.keys.first().fraOgMed
    val tilOgMed: LocalDate = ordinær.keys.last().tilOgMed

    fun forMåned(måned: Måned, satsKategori: Satskategori): MinsteÅrligYtelseForUføretrygdedeForMåned {
        val satser = when (satsKategori) {
            Satskategori.ORDINÆR -> ordinær
            Satskategori.HØY -> høy
        }
        return satser[måned]
            ?: throw IllegalStateException("Har ikke minste årlige ytelse for uføretrygdede for måned: $måned. Vi har bare data for perioden: ${satser.periode()}")
    }
}
