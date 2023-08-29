package no.nav.su.se.bakover.domain.skatt

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.tid.YearRange
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

    fun hentMestGyldigeSkattegrunnlagEllerFeil(): Either<KunneIkkeHenteSkattemelding, SamletSkattegrunnlagForÅrOgStadie> {
        return when (val oppgjørRes = this.oppgjør.resultat) {
            is SamletSkattegrunnlagForÅrOgStadie.Resultat.Feil -> oppgjørRes.originalFeil.left()
            is SamletSkattegrunnlagForÅrOgStadie.Resultat.Finnes -> oppgjørRes.value.right()
            SamletSkattegrunnlagForÅrOgStadie.Resultat.FinnesIkke -> when (val utkastRes = this.utkast.resultat) {
                is SamletSkattegrunnlagForÅrOgStadie.Resultat.Feil -> utkastRes.originalFeil.left()
                is SamletSkattegrunnlagForÅrOgStadie.Resultat.Finnes -> utkastRes.value.right()
                SamletSkattegrunnlagForÅrOgStadie.Resultat.FinnesIkke -> KunneIkkeHenteSkattemelding.FinnesIkke.left()
            }
        }
    }
}

fun List<SamletSkattegrunnlagForÅr>.toYearRange(): YearRange =
    YearRange(this.minOf { it.år }, this.maxOf { it.år })
