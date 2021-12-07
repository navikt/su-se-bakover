package no.nav.su.se.bakover.domain.revurdering

import no.nav.su.se.bakover.common.startOfMonth
import no.nav.su.se.bakover.domain.oppdrag.Avkortingsvarsel
import java.time.LocalDate

@JvmInline
value class OpphørsdatoForUtbetalinger private constructor(
    private val opphørsdato: LocalDate,
) {
    constructor(
        revurdering: BeregnetRevurdering.Opphørt,
        avkortingsvarsel: Avkortingsvarsel,
    ) : this(bestem(revurdering, avkortingsvarsel))

    constructor(
        revurdering: SimulertRevurdering.Opphørt,
    ) : this(bestem(revurdering, revurdering.avkortingsvarsel))

    constructor(
        revurdering: RevurderingTilAttestering.Opphørt,
    ) : this(bestem(revurdering, revurdering.avkortingsvarsel))

    fun get(): LocalDate = opphørsdato

    private companion object {
        fun bestem(
            revurdering: Revurdering,
            avkortingsvarsel: Avkortingsvarsel,
        ): LocalDate {
            return when (avkortingsvarsel) {
                is Avkortingsvarsel.Ingen -> {
                    revurdering.periode.fraOgMed
                }
                is Avkortingsvarsel.Utenlandsopphold -> {
                    val tidligsteIkkeUtbetalteMåned = avkortingsvarsel.hentUtbetalteBeløp(revurdering.periode)
                        .maxOf { it.first.tilOgMed }
                        .plusMonths(1)
                        .startOfMonth()

                    check(revurdering.periode inneholder tidligsteIkkeUtbetalteMåned) { "Opphørsdato er utenfor revurderingsperioden" }
                    tidligsteIkkeUtbetalteMåned
                }
            }
        }
    }
}
