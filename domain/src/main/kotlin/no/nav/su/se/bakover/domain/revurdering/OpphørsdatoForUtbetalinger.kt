package no.nav.su.se.bakover.domain.revurdering

import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.startOfMonth
import no.nav.su.se.bakover.domain.avkorting.AvkortingVedRevurdering
import no.nav.su.se.bakover.domain.avkorting.Avkortingsvarsel

@JvmInline
value class OpphørsdatoForUtbetalinger private constructor(
    val value: Periode,
) {
    companion object {
        operator fun invoke(
            revurdering: BeregnetRevurdering.Opphørt,
            avkortingsvarsel: Avkortingsvarsel,
        ): OpphørsdatoForUtbetalinger {
            return bestem(
                revurdering = revurdering,
                avkortingsvarsel = avkortingsvarsel,
            )
        }

        operator fun invoke(
            revurdering: SimulertRevurdering.Opphørt,
        ): OpphørsdatoForUtbetalinger {
            return bestem(
                revurdering = revurdering,
                avkortingsvarsel = revurdering.avkorting.avkortingsvarsel()
            )
        }

        operator fun invoke(
            revurdering: RevurderingTilAttestering.Opphørt,
        ): OpphørsdatoForUtbetalinger {
            return bestem(
                revurdering = revurdering,
                avkortingsvarsel = revurdering.avkorting.avkortingsvarsel()
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
        ): OpphørsdatoForUtbetalinger {
            return when (avkortingsvarsel) {
                is Avkortingsvarsel.Ingen -> {
                    OpphørsdatoForUtbetalinger(revurdering.periode)
                }
                is Avkortingsvarsel.Utenlandsopphold -> {
                    val tidligsteIkkeUtbetalteMåned = avkortingsvarsel.hentUtbetalteBeløp()
                        .senesteDato()
                        .plusMonths(1)
                        .startOfMonth()

                    check(revurdering.periode inneholder tidligsteIkkeUtbetalteMåned) { "Opphørsdato er utenfor revurderingsperioden" }
                    OpphørsdatoForUtbetalinger(Periode.create(tidligsteIkkeUtbetalteMåned, revurdering.periode.tilOgMed))
                }
            }
        }
    }
}
