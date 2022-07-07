package no.nav.su.se.bakover.domain.visitor

import no.nav.su.se.bakover.domain.revurdering.IverksattRevurdering
import no.nav.su.se.bakover.domain.revurdering.Revurderingsårsak
import no.nav.su.se.bakover.domain.vedtak.Avslagsvedtak
import no.nav.su.se.bakover.domain.vedtak.VedtakSomKanRevurderes
import no.nav.su.se.bakover.domain.vedtak.VedtakVisitor
import kotlin.properties.Delegates

internal class SkalSendeBrevVisitor : VedtakVisitor {
    var sendBrev by Delegates.notNull<Boolean>()

    override fun visit(vedtak: VedtakSomKanRevurderes.EndringIYtelse.InnvilgetSøknadsbehandling) {
        sendBrev = !vedtak.innvilgetGRegulering()
    }

    override fun visit(vedtak: VedtakSomKanRevurderes.EndringIYtelse.InnvilgetRevurdering) {
        sendBrev = !vedtak.innvilgetGRegulering() && vedtak.behandling.tilbakekrevingErVurdert().isLeft()
    }

    override fun visit(vedtak: VedtakSomKanRevurderes.EndringIYtelse.InnvilgetRegulering) {
        sendBrev = false
    }

    override fun visit(vedtak: VedtakSomKanRevurderes.EndringIYtelse.OpphørtRevurdering) {
        sendBrev = !vedtak.innvilgetGRegulering() && vedtak.behandling.tilbakekrevingErVurdert().isLeft()
    }

    override fun visit(vedtak: Avslagsvedtak.AvslagVilkår) {
        sendBrev = true
    }

    override fun visit(vedtak: Avslagsvedtak.AvslagBeregning) {
        sendBrev = true
    }

    override fun visit(vedtak: VedtakSomKanRevurderes.IngenEndringIYtelse) {
        sendBrev = !vedtak.årsakErGRegulering() && vedtak.sendBrevErValgt()
    }

    override fun visit(vedtak: VedtakSomKanRevurderes.EndringIYtelse.StansAvYtelse) {
        sendBrev = false
    }

    override fun visit(vedtak: VedtakSomKanRevurderes.EndringIYtelse.GjenopptakAvYtelse) {
        sendBrev = false
    }

    private fun VedtakSomKanRevurderes.årsakErGRegulering(): Boolean {
        return (behandling as IverksattRevurdering).revurderingsårsak.årsak == Revurderingsårsak.Årsak.REGULER_GRUNNBELØP
    }

    private fun VedtakSomKanRevurderes.EndringIYtelse.innvilgetGRegulering(): Boolean {
        return behandling is IverksattRevurdering.Innvilget && årsakErGRegulering()
    }

    private fun VedtakSomKanRevurderes.IngenEndringIYtelse.sendBrevErValgt(): Boolean {
        return behandling.skalFøreTilUtsendingAvVedtaksbrev
    }

    /**
     * Note to self: forsøk på å erstatte følgende spredt logikk for utsending av  brev for vedtak
     (this.behandling as? IverksattRevurdering.Innvilget)?.revurderingsårsak?.årsak != Revurderingsårsak.Årsak.REGULER_GRUNNBELØP (Vedtak.skalSendeBrev)
     vedtak.behandling is IverksattRevurdering.IngenEndring && !(vedtak.behandling as IverksattRevurdering.IngenEndring).skalFøreTilBrevutsending ()
     if (revurdering.revurderingsårsak.årsak == REGULER_GRUNNBELØP) false else request.skalFøreTilBrevutsending, (Revurderingservice.sendTilAttestering)
     */
}
