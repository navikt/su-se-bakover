package no.nav.su.se.bakover.domain.skatt

import arrow.core.Either
import no.nav.su.se.bakover.common.YearRange
import java.time.Year

sealed class SamletSkattegrunnlagForÅrOgStadie {
    abstract val oppslag: Either<KunneIkkeHenteSkattemelding, Skattegrunnlag.SkattegrunnlagForÅr>
    abstract val inntektsår: Year

    sealed interface Resultat {
        object FinnesIkke : Resultat
        data class Feil(val originalFeil: KunneIkkeHenteSkattemelding) : Resultat
        data class Finnes(val value: SamletSkattegrunnlagForÅrOgStadie) : Resultat
    }

    abstract val resultat: Resultat

    data class Oppgjør(
        override val oppslag: Either<KunneIkkeHenteSkattemelding, Skattegrunnlag.SkattegrunnlagForÅr>,
        override val inntektsår: Year,
    ) : SamletSkattegrunnlagForÅrOgStadie() {

        override val resultat: Resultat = when (oppslag) {
            is Either.Left -> when (this.oppslag.value) {
                is KunneIkkeHenteSkattemelding.FinnesIkke -> Resultat.FinnesIkke
                KunneIkkeHenteSkattemelding.ManglerRettigheter,
                KunneIkkeHenteSkattemelding.Nettverksfeil,
                KunneIkkeHenteSkattemelding.PersonFeil,
                KunneIkkeHenteSkattemelding.UkjentFeil,
                -> Resultat.Feil(this.oppslag.value)
            }

            is Either.Right -> Resultat.Finnes(this)
        }
    }

    data class Utkast(
        override val oppslag: Either<KunneIkkeHenteSkattemelding, Skattegrunnlag.SkattegrunnlagForÅr>,
        override val inntektsår: Year,
    ) : SamletSkattegrunnlagForÅrOgStadie() {
        override val resultat: Resultat = when (oppslag) {
            is Either.Left -> when (this.oppslag.value) {
                is KunneIkkeHenteSkattemelding.FinnesIkke -> Resultat.FinnesIkke
                KunneIkkeHenteSkattemelding.ManglerRettigheter,
                KunneIkkeHenteSkattemelding.Nettverksfeil,
                KunneIkkeHenteSkattemelding.PersonFeil,
                KunneIkkeHenteSkattemelding.UkjentFeil,
                -> Resultat.Feil(this.oppslag.value)
            }

            is Either.Right -> Resultat.Finnes(this)
        }
    }
}

fun List<SamletSkattegrunnlagForÅrOgStadie>.toYearRange(): YearRange =
    YearRange(this.minOf { it.inntektsår }, this.maxOf { it.inntektsår })
