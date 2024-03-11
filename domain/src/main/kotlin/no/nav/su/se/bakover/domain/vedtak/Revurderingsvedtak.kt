package no.nav.su.se.bakover.domain.vedtak

import beregning.domain.Beregning
import no.nav.su.se.bakover.domain.revurdering.IverksattRevurdering
import vedtak.domain.VedtakSomKanRevurderes
import økonomi.domain.simulering.Simulering

/**
 * Grupperer de forskjellige revurderingsvedtakene, så man får et felles abstraksjonnivå.
 * Undertyper:
 * - [VedtakInnvilgetRevurdering]
 * - [VedtakOpphørMedUtbetaling]
 *
 * Se [Opphørsvedtak] for tilsvarende for opphørsvedtak.
 *
 */
sealed interface Revurderingsvedtak : VedtakSomKanRevurderes {
    override val behandling: IverksattRevurdering
    override val beregning: Beregning
    override val simulering: Simulering
}
