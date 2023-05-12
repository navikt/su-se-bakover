package no.nav.su.se.bakover.domain.skatt

import no.nav.su.se.bakover.common.YearRange
import java.time.Year

data class SamletSkattegrunnlagForÅr(
    val utkast: SamletSkattegrunnlagForÅrOgStadie.Utkast,
    val oppgjør: SamletSkattegrunnlagForÅrOgStadie.Oppgjør,
    val år: Year,
) {
    fun hentMestGyldigeSkattegrunnlag(): SamletSkattegrunnlagForÅrOgStadie {
        return when (oppgjør.resultat) {
            is SamletSkattegrunnlagForÅrOgStadie.Resultat.Feil,
            is SamletSkattegrunnlagForÅrOgStadie.Resultat.Finnes,
            -> oppgjør
            is SamletSkattegrunnlagForÅrOgStadie.Resultat.FinnesIkke -> utkast
        }
    }
}

fun List<SamletSkattegrunnlagForÅr>.toYearRange(): YearRange =
    YearRange(this.minOf { it.år }, this.maxOf { it.år })
