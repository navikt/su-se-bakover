package no.nav.su.se.bakover.domain.revurdering

import no.nav.su.se.bakover.common.startOfMonth
import no.nav.su.se.bakover.domain.avkorting.Avkortingsvarsel
import java.time.LocalDate

@JvmInline
value class OpphørsdatoForUtbetalinger private constructor(
    val value: LocalDate,
) {
    companion object {
        operator fun invoke(
            revurdering: BeregnetRevurdering.Opphørt,
            avkortingsvarsel: Avkortingsvarsel,
        ): OpphørsdatoForUtbetalinger {
            return bestem(revurdering, avkortingsvarsel)
        }

        operator fun invoke(
            revurdering: SimulertRevurdering.Opphørt,
        ): OpphørsdatoForUtbetalinger {
            return bestem(revurdering, revurdering.avkortingsvarsel)
        }

        operator fun invoke(
            revurdering: RevurderingTilAttestering.Opphørt,
        ): OpphørsdatoForUtbetalinger {
            return bestem(revurdering, revurdering.avkortingsvarsel)
        }

        private fun bestem(
            revurdering: Revurdering,
            avkortingsvarsel: Avkortingsvarsel,
        ): OpphørsdatoForUtbetalinger {
            return when (avkortingsvarsel) {
                is Avkortingsvarsel.Ingen -> {
                    OpphørsdatoForUtbetalinger(revurdering.periode.fraOgMed)
                }
                is Avkortingsvarsel.Utenlandsopphold -> {
                    val tidligsteIkkeUtbetalteMåned = avkortingsvarsel.hentUtbetalteBeløp()
                        .senesteDato()
                        .plusMonths(1)
                        .startOfMonth()

                    check(revurdering.periode inneholder tidligsteIkkeUtbetalteMåned) { "Opphørsdato er utenfor revurderingsperioden" }
                    OpphørsdatoForUtbetalinger(tidligsteIkkeUtbetalteMåned)
                }
            }
        }
    }
}
