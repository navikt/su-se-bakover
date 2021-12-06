package no.nav.su.se.bakover.domain.revurdering

import no.nav.su.se.bakover.common.startOfMonth
import no.nav.su.se.bakover.domain.oppdrag.Feilutbetalingsvarsel
import java.time.LocalDate

@JvmInline
value class OpphørsdatoForUtbetalinger private constructor(
    private val opphørsdato: LocalDate,
) {
    constructor(
        revurdering: BeregnetRevurdering.Opphørt,
        feilutbetalingsvarsel: Feilutbetalingsvarsel,
    ) : this(bestem(revurdering, feilutbetalingsvarsel))

    constructor(
        revurdering: SimulertRevurdering.Opphørt,
    ) : this(bestem(revurdering, revurdering.feilutbetalingsvarsel))

    constructor(
        revurdering: RevurderingTilAttestering.Opphørt,
    ) : this(bestem(revurdering, revurdering.feilutbetalingsvarsel))

    fun get(): LocalDate = opphørsdato

    private companion object {
        fun bestem(
            revurdering: Revurdering,
            feilutbetalingsvarsel: Feilutbetalingsvarsel,
        ): LocalDate {
            return when (feilutbetalingsvarsel) {
                is Feilutbetalingsvarsel.Ingen -> {
                    revurdering.periode.fraOgMed
                }
                is Feilutbetalingsvarsel.KanAvkortes -> {
                    val tidligsteIkkeUtbetalteMåned = feilutbetalingsvarsel.hentUtbetalteBeløp(revurdering.periode)
                        .maxOf { it.first.tilOgMed }
                        .plusMonths(1)
                        .startOfMonth()

                    check(revurdering.periode inneholder tidligsteIkkeUtbetalteMåned) { "Opphørsdato er utenfor revurderingsperioden" }
                    tidligsteIkkeUtbetalteMåned
                }
                is Feilutbetalingsvarsel.MåTilbakekreves -> {
                    revurdering.periode.fraOgMed
                }
            }
        }
    }
}
