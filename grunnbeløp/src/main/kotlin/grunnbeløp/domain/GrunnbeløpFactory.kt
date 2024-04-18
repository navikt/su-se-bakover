package grunnbeløp.domain

import arrow.core.NonEmptyList
import no.nav.su.se.bakover.common.domain.Knekkpunkt
import no.nav.su.se.bakover.common.domain.Knekkpunkt.Companion.compareTo
import no.nav.su.se.bakover.common.domain.RåSats
import no.nav.su.se.bakover.common.domain.RåSatser
import no.nav.su.se.bakover.common.domain.extensions.toNonEmptyList
import no.nav.su.se.bakover.common.domain.periodisert
import no.nav.su.se.bakover.common.domain.tid.erSortertOgUtenDuplikater
import no.nav.su.se.bakover.common.tid.periode.Måned
import no.nav.su.se.bakover.common.tid.periode.erSammenhengendeSortertOgUtenDuplikater
import java.math.BigDecimal
import java.time.LocalDate
/**
 * Fra lov om supplerende stønad (https://lovdata.no/dokument/NL/lov/2005-04-29-21):
 * - Med grunnbeløpet meiner ein i lova her grunnbeløpet etter [folketrygdlova § 1-4](https://lovdata.no/dokument/NL/lov/1997-02-28-19/KAPITTEL_2-1#%C2%A71-4).
 * - Grunnbeløpet er en beregningsfaktor som har betydning for retten til ytelser og for størrelsen på ytelser etter denne loven.
 * - Grunnbeløpet fastsettes av Kongen og reguleres årlig med virkning fra 1. mai i samsvar med lønnsveksten.
 *
 * @param grunnbeløpsendringer liste med alle kjente endringer i grunnbeløp (kan også være fremtidige som enda ikke har trådt i kraft)
 *
 * @param knekkpunkt ikrafttredelsesdatoen til en gitt lov/sats. Brukes for å finne ut hvilke satser som gjaldt på en gitt dato.
 * @param tidligsteTilgjengeligeMåned Første måned denne satsen er aktuell. Som for denne satsen er 2021-01-01, men siden vi har tester som antar den gjelder før dette er den dynamisk.
 *
 */
class GrunnbeløpFactory(
    private val grunnbeløpsendringer: NonEmptyList<Grunnbeløpsendring>,
    val knekkpunkt: Knekkpunkt,
    val tidligsteTilgjengeligeMåned: Måned,
) {
    private val månedTilGrunnbeløp: Map<Måned, GrunnbeløpForMåned> =
        grunnbeløpsendringer.periodiserIftVirkningstidspunkt(knekkpunkt, tidligsteTilgjengeligeMåned)

    private val sisteMånedMedEndring = månedTilGrunnbeløp.keys.last()

    init {
        grunnbeløpsendringer.map { it.ikrafttredelse }.let {
            require(it.erSortertOgUtenDuplikater()) {
                "Grunnbeløp: Ikrafttredelse må være i stigende rekkefølge og uten duplikater, men var: $it}"
            }
        }
        grunnbeløpsendringer.map { it.virkningstidspunkt }.let {
            require(it.erSortertOgUtenDuplikater()) {
                "Grunnbeløp: Virkningstidspunkt må være i stigende rekkefølge og uten duplikater, men var: $it}"
            }
        }
        require(månedTilGrunnbeløp.values.all { it.ikrafttredelse <= knekkpunkt })
        require(månedTilGrunnbeløp.isNotEmpty())
        require(månedTilGrunnbeløp.erSammenhengendeSortertOgUtenDuplikater())
        require(månedTilGrunnbeløp.keys.first() == tidligsteTilgjengeligeMåned)
    }

    fun forMåned(måned: Måned): GrunnbeløpForMåned {
        return månedTilGrunnbeløp[måned]
            ?: if (måned > sisteMånedMedEndring) {
                månedTilGrunnbeløp[sisteMånedMedEndring]!!.copy(
                    måned = måned,
                )
            } else {
                throw IllegalArgumentException(
                    "Har ikke data for etterspurt måned: $måned. Vi har bare data fra og med måned: ${månedTilGrunnbeløp.keys.first()}",
                )
            }
    }

    fun alleGrunnbeløp(fraOgMed: Måned): List<GrunnbeløpForMåned> {
        return månedTilGrunnbeløp.filterValues {
            it.måned starterSamtidigEllerSenere fraOgMed
        }.values.toList()
    }

    fun alle(): List<GrunnbeløpForMåned> {
        return alleGrunnbeløp(månedTilGrunnbeløp.minOf { it.key })
    }
}

data class Grunnbeløpsendring(
    /** angir datoen endringen skal virke fra og med etter den har trådt i kraft */
    val virkningstidspunkt: LocalDate,
    /** angir datoen endringen trer i kraft */
    val ikrafttredelse: LocalDate,
    /** grunnbeløpet */
    val verdi: Int,
    /** Omregningsfaktoren viser hvor mye grunnbeløpet har økt fra året før. */
    val omregningsfaktor: BigDecimal,
)

private fun NonEmptyList<Grunnbeløpsendring>.periodiserIftVirkningstidspunkt(
    knekkpunkt: Knekkpunkt,
    tidligsteTilgjengeligeMåned: Måned,
): Map<Måned, GrunnbeløpForMåned> {
    return filterNot { it.ikrafttredelse > knekkpunkt }
        .map { RåSats(it.virkningstidspunkt, it) }
        .let {
            RåSatser(
                it.toNonEmptyList(),

            )
        }
        .periodisert(tidligsteTilgjengeligeMåned)
        .associate {
            it.måned to GrunnbeløpForMåned(
                måned = it.måned,
                grunnbeløpPerÅr = it.verdi.verdi,
                ikrafttredelse = it.verdi.ikrafttredelse,
                virkningstidspunkt = it.verdi.virkningstidspunkt,
                omregningsfaktor = it.verdi.omregningsfaktor,
            )
        }
}
