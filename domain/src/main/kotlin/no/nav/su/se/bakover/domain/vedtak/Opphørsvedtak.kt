package no.nav.su.se.bakover.domain.vedtak

import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.domain.avkorting.AvkortingVedRevurdering
import no.nav.su.se.bakover.domain.behandling.avslag.Opphørsgrunn
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.revurdering.IverksattRevurdering
import økonomi.domain.simulering.Simulering
import java.time.Clock

/**
 * Grupperer de forskjellige opphørsvedtakene, så man får et felles abstraksjonnivå.
 * Undertyper:
 * - [VedtakOpphørAvkorting]
 * - [VedtakOpphørMedUtbetaling]
 */
sealed interface Opphørsvedtak : VedtakSomKanRevurderes, Revurderingsvedtak {
    override val behandling: IverksattRevurdering.Opphørt
    override val beregning: Beregning
    override val simulering: Simulering

    override fun erInnvilget(): Boolean = false

    fun utledOpphørsgrunner(clock: Clock): List<Opphørsgrunn> = behandling.utledOpphørsgrunner(clock)

    override fun harIdentifisertBehovForFremtidigAvkorting() =
        behandling.avkorting is AvkortingVedRevurdering.Iverksatt.HarProdusertNyttAvkortingsvarsel

    fun harIdentifisertBehovForFremtidigAvkorting(periode: Periode): Boolean {
        return (behandling.avkorting as? AvkortingVedRevurdering.Iverksatt.HarProdusertNyttAvkortingsvarsel)?.periode()
            ?.overlapper(periode) ?: false
    }

    override fun erOpphør(): Boolean = true
    override fun erStans(): Boolean = false
    override fun erGjenopptak(): Boolean = false
}
