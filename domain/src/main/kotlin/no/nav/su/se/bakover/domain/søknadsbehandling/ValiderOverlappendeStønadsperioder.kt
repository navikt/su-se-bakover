package no.nav.su.se.bakover.domain.søknadsbehandling

import arrow.core.Either
import arrow.core.left
import arrow.core.raise.either
import arrow.core.right
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.domain.Sak

/**
 * Tar kun utgangspunkt i vedtak (ikke pågående behandlinger).
 * Begrensninger:
 * - Støtter ikke overlapp med ikke-opphørte måneder.
 */
internal fun Sak.validerOverlappendeStønadsperioder(
    periode: Periode,
): Either<StøtterIkkeOverlappendeStønadsperioder, Unit> {
    return either {
        validerOverlappendeIkkeOpphørtePerioder(periode).bind()
    }
}

private fun Sak.validerOverlappendeIkkeOpphørtePerioder(
    periode: Periode,
): Either<StøtterIkkeOverlappendeStønadsperioder, Unit> {
    hentIkkeOpphørtePerioder().let { stønadsperioder ->
        if (stønadsperioder.any { it overlapper periode }) {
            return StøtterIkkeOverlappendeStønadsperioder.StønadsperiodeOverlapperMedIkkeOpphørtStønadsperiode.left()
        }
        if (stønadsperioder.any { it.starterSamtidigEllerSenere(periode) }) {
            return StøtterIkkeOverlappendeStønadsperioder.StønadsperiodeForSenerePeriodeEksisterer.left()
        }
    }
    return Unit.right()
}

sealed interface StøtterIkkeOverlappendeStønadsperioder {
    data object StønadsperiodeOverlapperMedIkkeOpphørtStønadsperiode : StøtterIkkeOverlappendeStønadsperioder
    data object StønadsperiodeForSenerePeriodeEksisterer : StøtterIkkeOverlappendeStønadsperioder
}
