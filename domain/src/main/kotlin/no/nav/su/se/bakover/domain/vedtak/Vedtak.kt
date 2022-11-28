package no.nav.su.se.bakover.domain.vedtak

import arrow.core.Either
import arrow.core.getOrHandle
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.NavIdentBruker
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.domain.beregning.Beregning
import java.util.UUID

/**
 * Toppnivået av vedtak. Støtter både stønadsvedtak og klagevedtak.
 */
sealed interface Vedtak {
    val id: UUID
    val opprettet: Tidspunkt
    val saksbehandler: NavIdentBruker.Saksbehandler
    val attestant: NavIdentBruker.Attestant
}

/**
 * Henter beregningen for vedtakstyper hvis beregning kan være gjeldende på et gitt tidspunkt.
 * ##NB! Kaster hvis beregnignen ikke kan være gjeldende.
 */
fun VedtakSomKanRevurderes.hentBeregningForGjeldendeVedtak(): Beregning {
    return beregningKanVæreGjeldende().getOrHandle { throw IllegalStateException("${this::class} har ikke beregning") }
        .let {
            when (it) {
                is VedtakSomKanRevurderes.EndringIYtelse.InnvilgetRevurdering -> it.beregning
                is VedtakSomKanRevurderes.EndringIYtelse.InnvilgetSøknadsbehandling -> it.beregning
                is VedtakSomKanRevurderes.EndringIYtelse.OpphørtRevurdering -> it.beregning
                is VedtakSomKanRevurderes.EndringIYtelse.InnvilgetRegulering -> it.beregning
                else -> throw IllegalStateException("Inkonsistens mellom beregningKanVæreGjeldende() hentBeregningForGjeldendeVedtak()")
            }
        }
}

/**
 * Kan beregningen for aktuelt [Vedtak] være gjeldende; altså vært lagt til grunn for utbetalingene på saken?
 */
fun Vedtak.beregningKanVæreGjeldende(): Either<Unit, VedtakSomKanRevurderes> {
    return when (this) {
        is VedtakSomKanRevurderes.EndringIYtelse.InnvilgetRevurdering -> this.right()
        is VedtakSomKanRevurderes.EndringIYtelse.InnvilgetSøknadsbehandling -> this.right()
        is VedtakSomKanRevurderes.EndringIYtelse.OpphørtRevurdering -> this.right()
        is VedtakSomKanRevurderes.EndringIYtelse.InnvilgetRegulering -> this.right()
        is VedtakSomKanRevurderes.EndringIYtelse.StansAvYtelse, // ingen beregning, stanser utbetalinger beregnet av et annet vedtak
        is VedtakSomKanRevurderes.EndringIYtelse.GjenopptakAvYtelse, // ingen beregning, gjenopptar utbetalinger beregnet av et annet vedtak
        is Klagevedtak.Avvist,
        is Avslagsvedtak.AvslagBeregning,
        is Avslagsvedtak.AvslagVilkår,
        -> {
            Unit.left()
        }
    }
}
