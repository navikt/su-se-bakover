package tilbakekreving.domain

import dokument.domain.brev.Brevvalg
import tilbakekreving.domain.vurdering.VurderingerMedKrav

sealed interface ErUtfylt : Tilbakekrevingsbehandling {
    override val vurderingerMedKrav: VurderingerMedKrav
    override val vedtaksbrevvalg: Brevvalg.SaksbehandlersValg
}
