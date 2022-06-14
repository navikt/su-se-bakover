package no.nav.su.se.bakover.domain.grunnbeløp

import no.nav.su.se.bakover.common.erSortertOgUtenDuplikater
import no.nav.su.se.bakover.common.periode.Måned
import no.nav.su.se.bakover.common.periode.erSammenhengendeSortertOgUtenDuplikater
import no.nav.su.se.bakover.common.periode.periodisert
import no.nav.su.se.bakover.domain.satser.supplerendeStønadAlderFlyktningIkrafttredelse
import java.time.LocalDate
import java.time.YearMonth

/**
 * Fra lov om supplerende stønad (https://lovdata.no/dokument/NL/lov/2005-04-29-21):
 * - Med grunnbeløpet meiner ein i lova her grunnbeløpet etter [folketrygdlova § 1-4](https://lovdata.no/dokument/NL/lov/1997-02-28-19/KAPITTEL_2-1#%C2%A71-4).
 * - Grunnbeløpet er en beregningsfaktor som har betydning for retten til ytelser og for størrelsen på ytelser etter denne loven.
 * - Grunnbeløpet fastsettes av Kongen og reguleres årlig med virkning fra 1. mai i samsvar med lønnsveksten.
 *
 * @param påDato angir nåtiden for dennee instansen. Styrer hvilke grunnbeløpsendringer denne insatnsen kjenner til, basert på
 * kombinasjonen av *påDato* og [Grunnbeløpsendring.ikrafttredelse]. Instansen vil altså bare "se" endringer som har trådt i kraft på gitt dato.
 * @param grunnbeløpsendringer liste med alle kjente endringer i grunnbeløp (kan også være fremtidige som enda ikke har trådt i kraft)
 *
 */
class GrunnbeløpFactory(
    /** Hvordan verden så ut på denne datoen. */
    val påDato: LocalDate,
    /** Liste over alle kjente grunnbeløpsendringer. Disse vil bli filtrert basert på [påDato] */
    private val grunnbeløpsendringer: List<Grunnbeløpsendring>,
) {
    private val månedTilGrunnbeløp = grunnbeløpsendringer.periodiserIftVirkningstidspunkt(påDato)

    init {
        require(grunnbeløpsendringer.map { it.ikrafttredelse }.erSortertOgUtenDuplikater()) {
            "Ikrafttredelse for minste årlig ytelse for uføretrygdede må være i stigende rekkefølge og uten duplikater, men var: ${grunnbeløpsendringer.map { it.virkningstidspunkt }}"
        }
        require(grunnbeløpsendringer.map { it.virkningstidspunkt }.erSortertOgUtenDuplikater()) {
            "Virkningstidspunkt for minste årlig ytelse for uføretrygdede må være i stigende rekkefølge og uten duplikater, men var: ${grunnbeløpsendringer.map { it.virkningstidspunkt }}"
        }
        assert(månedTilGrunnbeløp.any { it.key.inneholder(supplerendeStønadAlderFlyktningIkrafttredelse) })
        assert(månedTilGrunnbeløp.isNotEmpty())
        månedTilGrunnbeløp.erSammenhengendeSortertOgUtenDuplikater()
    }

    fun forMåned(måned: Måned): GrunnbeløpForMåned {
        return månedTilGrunnbeløp[måned]!!
    }

    fun alleGrunnbeløp(fraOgMed: LocalDate): List<GrunnbeløpForMåned> {
        return månedTilGrunnbeløp.filterValues {
            it.måned starterSamtidigEllerSenere Måned.fra(
                YearMonth.of(
                    fraOgMed.year,
                    fraOgMed.month,
                ),
            )
        }.values.toList()
    }

    fun alle(): List<GrunnbeløpForMåned> {
        return alleGrunnbeløp(månedTilGrunnbeløp.minOf { it.key.fraOgMed })
    }
}

data class Grunnbeløpsendring(
    /** angir datoen endringen skal virke fra og med etter den har trådt i kraft */
    val virkningstidspunkt: LocalDate,
    /** angir datoen endringen trer i kraft */
    val ikrafttredelse: LocalDate,
    /** grunnbeløpet */
    val verdi: Int,
)

private fun List<Grunnbeløpsendring>.periodiserIftVirkningstidspunkt(senesteIkrafttredelse: LocalDate): Map<Måned, GrunnbeløpForMåned> {
    return filterNot { it.ikrafttredelse > senesteIkrafttredelse }
        .map { it.virkningstidspunkt to it }
        .periodisert()
        .associate { (virkningstidspunkt, måned, grunnbeløpsendring) ->
            måned to GrunnbeløpForMåned(
                måned = måned,
                grunnbeløpPerÅr = grunnbeløpsendring.verdi,
                ikrafttredelse = grunnbeløpsendring.ikrafttredelse,
                virkningstidspunkt = virkningstidspunkt,
            )
        }
}
