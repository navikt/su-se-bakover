package no.nav.su.se.bakover.domain.vedtak

import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.avkorting.AvkortingVedRevurdering
import no.nav.su.se.bakover.domain.behandling.avslag.Opphørsgrunn
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.revurdering.IverksattRevurdering
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

    fun utledOpphørsgrunner(clock: Clock): List<Opphørsgrunn> {
        return behandling.utledOpphørsgrunner(clock)
    }

    /**
     *  Dersom dette er en tilbakekreving som avventer kravvgrunnlag, så ønsker vi ikke å sende brev før vi mottar kravgrunnlaget
     *  Brevutsending skjer i [no.nav.su.se.bakover.service.tilbakekreving.TilbakekrevingService.sendTilbakekrevingsvedtak]
     *  TODO: Er det mulig å flytte denne logikken til ut fra vedtaks-biten til en felles plass?
     */
    override fun skalGenerereDokumentVedFerdigstillelse(): Boolean {
        return behandling.skalSendeVedtaksbrev() && !behandling.avventerKravgrunnlag()
    }

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
