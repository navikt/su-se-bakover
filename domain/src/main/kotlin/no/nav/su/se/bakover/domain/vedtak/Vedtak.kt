package no.nav.su.se.bakover.domain.vedtak

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.NavIdentBruker
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.dokument.Dokumenttilstand
import java.util.UUID

/**
 * Toppnivået av vedtak. Støtter både stønadsvedtak og klagevedtak.
 */
sealed interface Vedtak {
    val id: UUID
    val opprettet: Tidspunkt
    val saksbehandler: NavIdentBruker.Saksbehandler
    val attestant: NavIdentBruker.Attestant
    val dokumenttilstand: Dokumenttilstand
}

/**
 * Henter beregningen for vedtakstyper hvis beregning kan være gjeldende på et gitt tidspunkt.
 * ##NB! Kaster hvis beregnignen ikke kan være gjeldende.
 */
fun VedtakSomKanRevurderes.hentBeregningForGjeldendeVedtak(): Beregning {
    return beregningKanVæreGjeldende().getOrElse { throw IllegalStateException("${this::class} har ikke beregning") }
        .let {
            when (it) {
                is VedtakInnvilgetRevurdering -> it.beregning
                is VedtakInnvilgetSøknadsbehandling -> it.beregning
                is VedtakOpphørtRevurdering -> it.beregning
                is VedtakInnvilgetRegulering -> it.beregning
                else -> throw IllegalStateException("Inkonsistens mellom beregningKanVæreGjeldende() hentBeregningForGjeldendeVedtak()")
            }
        }
}

/**
 * Kan beregningen for aktuelt [Vedtak] være gjeldende; altså vært lagt til grunn for utbetalingene på saken?
 */
fun Vedtak.beregningKanVæreGjeldende(): Either<Unit, VedtakSomKanRevurderes> {
    return when (this) {
        is VedtakInnvilgetRevurdering -> this.right()
        is VedtakInnvilgetSøknadsbehandling -> this.right()
        is VedtakOpphørtRevurdering -> this.right()
        is VedtakInnvilgetRegulering -> this.right()
        is VedtakStansAvYtelse, // ingen beregning, stanser utbetalinger beregnet av et annet vedtak
        is VedtakGjenopptakAvYtelse, // ingen beregning, gjenopptar utbetalinger beregnet av et annet vedtak
        is Klagevedtak.Avvist,
        is VedtakAvslagBeregning,
        is VedtakAvslagVilkår,
        -> {
            Unit.left()
        }
    }
}
