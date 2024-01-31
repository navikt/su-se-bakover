package vilkår.skatt.domain

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.tid.YearRange
import vilkår.skatt.domain.KunneIkkeHenteMestGyldigeSkattegrunnlag.Companion.tilKunneIkkeHenteMestGyldigeSkattegrunnlag
import java.time.Year

/**
 * Et hvert år inneholder 2 forskjellige skatte-stadier
 */
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

    /**
     * KunneIkkeHenteSkattemelding.FinnesIkke mappes om til å gi Skattegrunnlaget, der det er aktuelt.
     * Dette er fordi vi som oftest vil gjøre noe videre med skattegrunnlaget
     */
    fun hentMestGyldigeSkattegrunnlagEllerFeil(): Either<KunneIkkeHenteMestGyldigeSkattegrunnlag, SamletSkattegrunnlagForÅrOgStadie> {
        return when (val oppgjørRes = this.oppgjør.resultat) {
            is SamletSkattegrunnlagForÅrOgStadie.Resultat.Feil -> oppgjørRes.originalFeil.tilKunneIkkeHenteMestGyldigeSkattegrunnlag()
                .left()

            is SamletSkattegrunnlagForÅrOgStadie.Resultat.Finnes -> oppgjørRes.value.right()
            SamletSkattegrunnlagForÅrOgStadie.Resultat.FinnesIkke -> when (val utkastRes = this.utkast.resultat) {
                is SamletSkattegrunnlagForÅrOgStadie.Resultat.Feil -> utkastRes.originalFeil.tilKunneIkkeHenteMestGyldigeSkattegrunnlag()
                    .left()

                is SamletSkattegrunnlagForÅrOgStadie.Resultat.Finnes -> utkastRes.value.right()
                SamletSkattegrunnlagForÅrOgStadie.Resultat.FinnesIkke -> utkast.right()
            }
        }
    }
}

fun List<SamletSkattegrunnlagForÅr>.toYearRange(): YearRange =
    YearRange(this.minOf { it.år }, this.maxOf { it.år })
