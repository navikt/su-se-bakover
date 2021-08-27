package no.nav.su.se.bakover.domain.visitor

import no.nav.su.se.bakover.domain.revurdering.IverksattRevurdering
import no.nav.su.se.bakover.domain.revurdering.Revurderingsårsak
import no.nav.su.se.bakover.domain.vedtak.Vedtak
import no.nav.su.se.bakover.domain.vedtak.VedtakVisitor
import kotlin.properties.Delegates

internal class SkalSendeBrevVisitor : VedtakVisitor {
    var sendBrev by Delegates.notNull<Boolean>()

    override fun visit(vedtak: Vedtak.EndringIYtelse) {
        sendBrev = !vedtak.innvilgetGRegulering()
    }

    override fun visit(vedtak: Vedtak.Avslag.AvslagVilkår) {
        sendBrev = true
    }

    override fun visit(vedtak: Vedtak.Avslag.AvslagBeregning) {
        sendBrev = true
    }

    override fun visit(vedtak: Vedtak.IngenEndringIYtelse) {
        sendBrev = !vedtak.årsakErGRegulering() && vedtak.sendBrevErValgt()
    }

    private fun Vedtak.årsakErGRegulering(): Boolean {
        return (behandling as IverksattRevurdering).revurderingsårsak.årsak == Revurderingsårsak.Årsak.REGULER_GRUNNBELØP
    }

    private fun Vedtak.EndringIYtelse.innvilgetGRegulering(): Boolean {
        return behandling is IverksattRevurdering.Innvilget && årsakErGRegulering()
    }

    private fun Vedtak.IngenEndringIYtelse.sendBrevErValgt(): Boolean {
        return (behandling as IverksattRevurdering.IngenEndring).skalFøreTilBrevutsending
    }

    /**
     * Note to self: forsøk på å erstatte følgende spredt logikk for utsending av  brev for vedtak
     (this.behandling as? IverksattRevurdering.Innvilget)?.revurderingsårsak?.årsak != Revurderingsårsak.Årsak.REGULER_GRUNNBELØP (Vedtak.skalSendeBrev)
     vedtak.behandling is IverksattRevurdering.IngenEndring && !(vedtak.behandling as IverksattRevurdering.IngenEndring).skalFøreTilBrevutsending ()
     if (revurdering.revurderingsårsak.årsak == REGULER_GRUNNBELØP) false else request.skalFøreTilBrevutsending, (Revurderingservice.sendTilAttestering)
     */
}
