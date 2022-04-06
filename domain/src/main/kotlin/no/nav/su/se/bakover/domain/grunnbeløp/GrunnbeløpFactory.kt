package no.nav.su.se.bakover.domain.grunnbeløp

import no.nav.su.se.bakover.common.periode.Månedsperiode
import no.nav.su.se.bakover.common.periode.erSammenhengendeSortertOgUtenDuplikater
import no.nav.su.se.bakover.common.periode.periodisert
import no.nav.su.se.bakover.domain.satser.supplerendeStønadAlderFlyktningIkrafttredelse
import java.time.LocalDate

/**
 * Fra lov om supplerende stønad (https://lovdata.no/dokument/NL/lov/2005-04-29-21):
 * - Med grunnbeløpet meiner ein i lova her grunnbeløpet etter [folketrygdlova § 1-4](https://lovdata.no/dokument/NL/lov/1997-02-28-19/KAPITTEL_2-1#%C2%A71-4).
 * - Grunnbeløpet er en beregningsfaktor som har betydning for retten til ytelser og for størrelsen på ytelser etter denne loven.
 * - Grunnbeløpet fastsettes av Kongen og reguleres årlig med virkning fra 1. mai i samsvar med lønnsveksten.
 */
class GrunnbeløpFactory(
    val månedsperioder: Map<Månedsperiode, GrunnbeløpForMåned>,
) {
    init {
        assert(månedsperioder.any { it.key.inneholder(supplerendeStønadAlderFlyktningIkrafttredelse) })
        assert(månedsperioder.isNotEmpty())
        månedsperioder.erSammenhengendeSortertOgUtenDuplikater()
    }

    companion object {
        /**
         * Supplerende Stønad Alder gjelder fra 2006-01-01, så vi krever kontinuerlige verdier fra det tidspunktet.
         */
        fun createFromGrunnbeløp(grunnbeløp: List<Pair<LocalDate, Int>>): GrunnbeløpFactory {
            return GrunnbeløpFactory(
                månedsperioder = grunnbeløp.periodisert().associate { månedsperiode ->
                    månedsperiode.second to GrunnbeløpForMåned(
                        måned = månedsperiode.second,
                        grunnbeløpPerÅr = månedsperiode.third,
                        ikrafttredelse = månedsperiode.first,
                    )
                },
            )
        }
    }

    fun forMåned(måned: Månedsperiode): GrunnbeløpForMåned {
        return månedsperioder[måned]!!
    }
}
