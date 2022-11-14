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
        sendBrev = true
    }

    override fun visit(vedtak: VedtakSomKanRevurderes.EndringIYtelse.InnvilgetRevurdering) {
        sendBrev = !vedtak.innvilgetGRegulering() &&
            vedtak.behandling.brevvalgRevurdering.skalSendeBrev().isRight() &&
            /** Enn så lenge unngår vi å svare ja dersom vi er i et tilbakekrevingsløp, utsending av brev håndteres i løpe for kravgrunnlag*/
            vedtak.behandling.tilbakekrevingErVurdert().isLeft()
    }

    override fun visit(vedtak: VedtakSomKanRevurderes.EndringIYtelse.InnvilgetRegulering) {
        sendBrev = false
    }

    override fun visit(vedtak: VedtakSomKanRevurderes.EndringIYtelse.OpphørtRevurdering) {
        sendBrev = !vedtak.innvilgetGRegulering() &&
            vedtak.behandling.brevvalgRevurdering.skalSendeBrev().isRight() &&
            /** Enn så lenge unngår vi å svare ja dersom vi er i et tilbakekrevingsløp */
            vedtak.behandling.tilbakekrevingErVurdert().isLeft()
    }

    override fun visit(vedtak: Avslagsvedtak.AvslagVilkår) {
        sendBrev = true
    }

    override fun visit(vedtak: Avslagsvedtak.AvslagBeregning) {
        sendBrev = true
    }

    override fun visit(vedtak: VedtakSomKanRevurderes.IngenEndringIYtelse) {
        sendBrev = !vedtak.årsakErGRegulering() &&
            vedtak.behandling.brevvalgRevurdering.skalSendeBrev().isRight()
    }

    override fun visit(vedtak: VedtakSomKanRevurderes.EndringIYtelse.StansAvYtelse) {
        sendBrev = vedtak.behandling.brevvalgRevurdering.skalSendeBrev().isRight()
    }

    override fun visit(vedtak: VedtakSomKanRevurderes.EndringIYtelse.GjenopptakAvYtelse) {
        sendBrev = vedtak.behandling.brevvalgRevurdering.skalSendeBrev().isRight()
    }

    private fun VedtakSomKanRevurderes.årsakErGRegulering(): Boolean {
        return (behandling as IverksattRevurdering).revurderingsårsak.årsak == Revurderingsårsak.Årsak.REGULER_GRUNNBELØP
    }

    private fun VedtakSomKanRevurderes.EndringIYtelse.innvilgetGRegulering(): Boolean {
        return behandling is IverksattRevurdering.Innvilget &&
            årsakErGRegulering()
    }
}
