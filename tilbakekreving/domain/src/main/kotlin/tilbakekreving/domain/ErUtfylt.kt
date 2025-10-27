package tilbakekreving.domain

import dokument.domain.brev.Brevvalg
import tilbakekreving.domain.kravgrunnlag.Kravgrunnlag
import tilbakekreving.domain.vurdering.VurderingerMedKrav

/**
 * At behandlingen erUtfylt betyr at alle påkrevde handlinger fra saksbehandler er gjennomført, og behandlingen kan sendes til attestering.
 *
 * Konkret har dem tatt stilling til:
 * - Forhåndsvarsel*
 * - Vurdering av kravene
 * - Notat*
 * - Tekst til vedtaksbrev
 *
 * *= Dem har tatt stilling til - men ikke nødvendigvis fyllt ut.
 */
sealed interface ErUtfylt : Tilbakekrevingsbehandling {
    fun minstEnPeriodeSkalTilbakekreves(): Boolean = vurderingerMedKrav.minstEnPeriodeSkalTilbakekreves()

    override val vurderingerMedKrav: VurderingerMedKrav
    override val vedtaksbrevvalg: Brevvalg.SaksbehandlersValg
    override val kravgrunnlag: Kravgrunnlag
}
