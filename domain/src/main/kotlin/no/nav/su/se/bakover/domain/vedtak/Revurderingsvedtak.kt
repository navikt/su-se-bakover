package no.nav.su.se.bakover.domain.vedtak

import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.revurdering.IverksattRevurdering

/**
 * Grupperer de forskjellige revurderingsvedtakene, så man får et felles abstraksjonnivå.
 * Undertyper:
 * - [VedtakInnvilgetRevurdering]
 * - [VedtakOpphørAvkorting]
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
