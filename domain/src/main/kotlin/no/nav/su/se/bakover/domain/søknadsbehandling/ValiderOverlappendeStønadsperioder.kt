package no.nav.su.se.bakover.domain.søknadsbehandling

import arrow.core.Either
import arrow.core.continuations.either
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.domain.Sak
import java.time.Clock

/**
 * Tar kun utgangspunkt i vedtak (ikke pågående behandlinger).
 * Begrensninger:
 * - Støtter ikke overlapp med ikke-opphørte måneder.
 * - Støtter ikke overlapp med opphørte måneder som førte til avkorting.
 */
internal fun Sak.validerOverlappendeStønadsperioder(
    periode: Periode,
    clock: Clock,
): Either<StøtterIkkeOverlappendeStønadsperioder, Unit> {
    return either.eager {
        validerOverlappendeIkkeOpphørtePerioder(periode).bind()
        validerOpphørFørtTilAvkorting(periode, clock).bind()
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

private fun Sak.validerOpphørFørtTilAvkorting(
    periode: Periode,
    clock: Clock,
): Either<StøtterIkkeOverlappendeStønadsperioder.StønadsperiodeInneholderAvkortingPgaUtenlandsopphold, Unit> {
    return hentGjeldendeVedtaksdata(
        periode = periode,
        clock = clock,
    ).fold(
        { Unit.right() },
        {
            if (it.inneholderOpphørsvedtakMedAvkortingUtenlandsopphold()) {
                StøtterIkkeOverlappendeStønadsperioder.StønadsperiodeInneholderAvkortingPgaUtenlandsopphold.left()
            } else {
                Unit.right()
            }
        },
    )
}

sealed interface StøtterIkkeOverlappendeStønadsperioder {
    data object StønadsperiodeOverlapperMedIkkeOpphørtStønadsperiode : StøtterIkkeOverlappendeStønadsperioder
    data object StønadsperiodeForSenerePeriodeEksisterer : StøtterIkkeOverlappendeStønadsperioder
    data object StønadsperiodeInneholderAvkortingPgaUtenlandsopphold : StøtterIkkeOverlappendeStønadsperioder
}
