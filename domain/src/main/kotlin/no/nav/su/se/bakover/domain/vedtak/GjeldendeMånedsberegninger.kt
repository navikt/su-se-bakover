package no.nav.su.se.bakover.domain.vedtak

import no.nav.su.se.bakover.common.periode.Periode
import java.time.Clock

internal class GjeldendeMånedsberegninger(
    periode: Periode,
    vedtakListe: List<VedtakSomKanRevurderes>,
    clock: Clock = Clock.systemUTC(),
) {
    val månedsberegninger = vedtakListe
        .filter {
            when (it) {
                is Vedtak.EndringIYtelse.GjenopptakAvYtelse -> false // Har ingen ny beregning
                is Vedtak.EndringIYtelse.InnvilgetRevurdering -> true
                is Vedtak.EndringIYtelse.InnvilgetSøknadsbehandling -> true
                is Vedtak.EndringIYtelse.OpphørtRevurdering -> true
                is Vedtak.EndringIYtelse.StansAvYtelse -> false // Har ingen ny beregning
                is Vedtak.IngenEndringIYtelse -> false // Endringer trer aldri i kraft
            }
        }
        .lagTidslinje(periode, clock)
        .tidslinje
        .flatMap { vedtakPåTidslinje ->
            val beregning = when (vedtakPåTidslinje.originaltVedtak) {
                is Vedtak.EndringIYtelse.GjenopptakAvYtelse -> throw IllegalStateException()
                is Vedtak.EndringIYtelse.InnvilgetRevurdering -> vedtakPåTidslinje.originaltVedtak.beregning
                is Vedtak.EndringIYtelse.InnvilgetSøknadsbehandling -> vedtakPåTidslinje.originaltVedtak.beregning
                is Vedtak.EndringIYtelse.OpphørtRevurdering -> vedtakPåTidslinje.originaltVedtak.beregning
                is Vedtak.EndringIYtelse.StansAvYtelse -> throw IllegalStateException()
                is Vedtak.IngenEndringIYtelse -> throw IllegalStateException()
            }
            beregning.getMånedsberegninger().filter { månedsberegning ->
                vedtakPåTidslinje.periode.snitt(månedsberegning.periode) != null
            }
        }
}
