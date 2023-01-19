package no.nav.su.se.bakover.domain.revurdering.opphør

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.startOfMonth
import no.nav.su.se.bakover.domain.avkorting.AvkortingVedRevurdering
import no.nav.su.se.bakover.domain.avkorting.Avkortingsvarsel
import no.nav.su.se.bakover.domain.revurdering.BeregnetRevurdering
import no.nav.su.se.bakover.domain.revurdering.Revurdering
import no.nav.su.se.bakover.domain.revurdering.RevurderingTilAttestering
import no.nav.su.se.bakover.domain.revurdering.SimulertRevurdering

/**
 * I visse tilfeller vil ikke en behandlingsperiode og en utbetalingsperiode være like.
 * Foreløpig gjelder det kun avkorting etter utenlandsopphold.
 * Vi ønsker ikke sende endringer for de månedene vi avkorter, men for de andre månedene som opphører av andre grunner.
 * Andre grunner kan være feilutbetaling som førte eller ikke førte til tilbakekreving, eller opphør av måneder som ikke har vært utbetalt før.
 *
 * TODO jah: Vi støtter per tidspunkt ikke tomme opphørsperioder. Ved rene avkortingsopphør, ønsker vi ikke sende utbetalingslinjer i det hele tatt; men det er det ikke støtte for enda.
 */
@JvmInline
value class OpphørsperiodeForUtbetalinger private constructor(
    val value: Periode,
) {
    companion object {
        operator fun invoke(
            revurdering: BeregnetRevurdering.Opphørt,
            avkortingsvarsel: Avkortingsvarsel,
        ): Either<OpphørMedAvkortingManglerMånedSomKanSendesTilOppdrag, OpphørsperiodeForUtbetalinger> {
            return bestem(
                revurdering = revurdering,
                avkortingsvarsel = avkortingsvarsel,
            )
        }

        operator fun invoke(
            revurdering: SimulertRevurdering.Opphørt,
        ): Either<OpphørMedAvkortingManglerMånedSomKanSendesTilOppdrag, OpphørsperiodeForUtbetalinger> {
            return bestem(
                revurdering = revurdering,
                avkortingsvarsel = revurdering.avkorting.avkortingsvarsel(),
            )
        }

        operator fun invoke(
            revurdering: RevurderingTilAttestering.Opphørt,
        ): Either<OpphørMedAvkortingManglerMånedSomKanSendesTilOppdrag, OpphørsperiodeForUtbetalinger> {
            return bestem(
                revurdering = revurdering,
                avkortingsvarsel = revurdering.avkorting.avkortingsvarsel(),
            )
        }

        private fun AvkortingVedRevurdering.Håndtert.avkortingsvarsel(): Avkortingsvarsel {
            return when (this) {
                is AvkortingVedRevurdering.Håndtert.AnnullerUtestående -> {
                    Avkortingsvarsel.Ingen
                }
                is AvkortingVedRevurdering.Håndtert.IngenNyEllerUtestående -> {
                    Avkortingsvarsel.Ingen
                }
                is AvkortingVedRevurdering.Håndtert.OpprettNyttAvkortingsvarsel -> {
                    avkortingsvarsel
                }
                is AvkortingVedRevurdering.Håndtert.OpprettNyttAvkortingsvarselOgAnnullerUtestående -> {
                    avkortingsvarsel
                }
                is AvkortingVedRevurdering.Håndtert.KanIkkeHåndteres -> {
                    Avkortingsvarsel.Ingen
                }
            }
        }

        private fun bestem(
            revurdering: Revurdering,
            avkortingsvarsel: Avkortingsvarsel,
        ): Either<OpphørMedAvkortingManglerMånedSomKanSendesTilOppdrag, OpphørsperiodeForUtbetalinger> {
            return when (avkortingsvarsel) {
                is Avkortingsvarsel.Ingen -> {
                    OpphørsperiodeForUtbetalinger(revurdering.periode).right()
                }
                is Avkortingsvarsel.Utenlandsopphold -> {
                    // Denne kan være en måned etter revurderingsperiode. Det er ikke sikkert denne er innenfor samme stønadsperiode eller at det eksisterer en utbetalingslinje for denne måneden.
                    val tidligsteIkkeUtbetalteMånedEtterAvkortingsperiode = avkortingsvarsel.hentUtbetalteBeløp()
                        .senesteDato()
                        .plusMonths(1)
                        .startOfMonth()

                    if (revurdering.periode inneholder tidligsteIkkeUtbetalteMånedEtterAvkortingsperiode) {
                        OpphørsperiodeForUtbetalinger(Periode.create(tidligsteIkkeUtbetalteMånedEtterAvkortingsperiode, revurdering.periode.tilOgMed)).right()
                    } else {
                        OpphørMedAvkortingManglerMånedSomKanSendesTilOppdrag(
                            revurderinsperiode = revurdering.periode,
                            tidligsteIkkeUtbetalteMånedEtterAvkortingsperiode = tidligsteIkkeUtbetalteMånedEtterAvkortingsperiode,
                        ).left()
                    }
                }
            }
        }
    }
}
