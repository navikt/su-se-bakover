package no.nav.su.se.bakover.domain.vilkår

import arrow.core.NonEmptyList
import no.nav.su.se.bakover.common.endOfMonth
import no.nav.su.se.bakover.common.periode.Måned
import no.nav.su.se.bakover.common.periode.erSammenhengendeSortertOgUtenDuplikater
import no.nav.su.se.bakover.common.startOfMonth
import no.nav.su.se.bakover.domain.grunnbeløp.GrunnbeløpForMåned
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth

class FormuegrenserFactory(
    private val månedTilFormuegrense: Map<Måned, FormuegrenseForMåned>,
) {

    init {
        assert(månedTilFormuegrense.erSammenhengendeSortertOgUtenDuplikater())
    }

    companion object {
        fun createFromGrunnbeløp(grunnbeløpForMåneder: NonEmptyList<GrunnbeløpForMåned>): FormuegrenserFactory {
            return FormuegrenserFactory(
                månedTilFormuegrense = grunnbeløpForMåneder.associate { grunnbeløpForMåned ->
                    grunnbeløpForMåned.måned to FormuegrenseForMåned(
                        grunnbeløpForMåned = grunnbeløpForMåned,
                    )
                },
            )
        }
    }

    /**
     * @throws ArrayIndexOutOfBoundsException dersom måneden er utenfor tidslinjen
     */
    fun forMåned(måned: Måned): FormuegrenseForMåned {
        return månedTilFormuegrense[måned]!!
    }

    fun forDato(dato: LocalDate): FormuegrenseForMåned {
        return forMåned(Måned.fra(dato.startOfMonth(), dato.endOfMonth()))
    }

    /**
     * En liste, sortert etter dato i synkende rekkefølge.
     * Hver dato gir tilhørende formuegrense per år.
     * E.g. (2021-05-01 to 53199.5) siden formuegrensen fom. 5. mai 2021 var en halv G. Grunnbeløpet var da 106399.
     */
    fun virkningstidspunkt(fraOgMed: YearMonth): List<Pair<LocalDate, BigDecimal>> {
        val førsteMåned = forMåned(Måned.fra(fraOgMed))
        return månedTilFormuegrense
            .filter { it.value.virkningstidspunkt >= førsteMåned.virkningstidspunkt }
            .map { it.value.virkningstidspunkt to it.value.formuegrense }
            .distinct()
            .sortedByDescending { it.first }
    }

    override fun equals(other: Any?): Boolean {
        return other is FormuegrenserFactory && other.månedTilFormuegrense == this.månedTilFormuegrense
    }

    override fun hashCode(): Int {
        return månedTilFormuegrense.hashCode()
    }
}
