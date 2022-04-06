package no.nav.su.se.bakover.domain.vilkår

import arrow.core.NonEmptyList
import no.nav.su.se.bakover.common.periode.Månedsperiode
import no.nav.su.se.bakover.common.periode.erSammenhengendeSortertOgUtenDuplikater
import no.nav.su.se.bakover.domain.grunnbeløp.GrunnbeløpForMåned
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth

class FormuegrenserFactory(
    private val månedsperioder: Map<Månedsperiode, FormuegrenseForMåned>,
) {

    init {
        assert(månedsperioder.erSammenhengendeSortertOgUtenDuplikater())
    }

    companion object {
        fun createFromGrunnbeløp(grunnbeløpForMåneder: NonEmptyList<GrunnbeløpForMåned>): FormuegrenserFactory {
            return FormuegrenserFactory(
                månedsperioder = grunnbeløpForMåneder.associate { grunnbeløpForMåned ->
                    grunnbeløpForMåned.måned to FormuegrenseForMåned(
                        grunnbeløpForMåned = grunnbeløpForMåned,
                    )
                },
            )
        }
    }

    /**
     * @throws ArrayIndexOutOfBoundsException dersom månedsperioden er utenfor tidslinjen
     */
    fun forMånedsperiode(månedsperiode: Månedsperiode): FormuegrenseForMåned {
        return månedsperioder[månedsperiode]!!
    }

    /**
     * En liste, sortert etter dato i synkende rekkefølge.
     * Hver dato gir tilhørende formuegrense per år.
     * E.g. (2021-05-01 to 53199.5) siden formuegrensen fom. 5. mai 2021 var en halv G. Grunnbeløpet var da 106399.
     */
    fun ikrafttredelser(fraOgMed: YearMonth): List<Pair<LocalDate, BigDecimal>> {
        return månedsperioder
            .filter { it.value.ikrafttredelse >= fraOgMed.atDay(1) }
            .map { it.value.ikrafttredelse to it.value.formuegrense }
            .distinct()
            .sortedByDescending { it.first }
    }
}
