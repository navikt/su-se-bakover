package no.nav.su.se.bakover.domain.grunnbeløp

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
 */
class GrunnbeløpFactory(
    private val månedTilGrunnbeløp: Map<Måned, GrunnbeløpForMåned>,
) {
    init {
        assert(månedTilGrunnbeløp.any { it.key.inneholder(supplerendeStønadAlderFlyktningIkrafttredelse) })
        assert(månedTilGrunnbeløp.isNotEmpty())
        månedTilGrunnbeløp.erSammenhengendeSortertOgUtenDuplikater()
    }

    companion object {
        /**
         * Supplerende Stønad Alder gjelder fra 2006-01-01, så vi krever kontinuerlige verdier fra det tidspunktet.
         */
        fun createFromGrunnbeløp(grunnbeløp: List<Pair<LocalDate, Int>>): GrunnbeløpFactory {
            return GrunnbeløpFactory(
                månedTilGrunnbeløp = grunnbeløp.periodisert().associate { (ikrafttredelse, måned, grunnbeløp) ->
                    måned to GrunnbeløpForMåned(
                        måned = måned,
                        grunnbeløpPerÅr = grunnbeløp,
                        ikrafttredelse = ikrafttredelse,
                    )
                },
            )
        }
    }

    fun forMåned(måned: Måned): GrunnbeløpForMåned {
        return månedTilGrunnbeløp[måned]!!
    }

    fun alleGrunnbeløp(fraOgMed: LocalDate): List<GrunnbeløpForMåned> {
        return månedTilGrunnbeløp.filterValues {
            it.måned starterSamtidigEllerSenere Måned(
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

    /**
     * Konstruerer en factory med grunnbeløpene som var gjeldende på [påDato] ved å fjerne alle grunnbeløp som har
     * en [GrunnbeløpForMåned.ikrafttredelse] senere enn [påDato].
     */
    fun gjeldende(påDato: LocalDate): GrunnbeløpFactory {
        return alle()
            .filterNot { it.ikrafttredelse > påDato }
            .map { it.ikrafttredelse to it.grunnbeløpPerÅr }
            .distinct()
            .let { createFromGrunnbeløp(it) }
    }
}
