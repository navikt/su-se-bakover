@file:Suppress("PackageDirectoryMismatch")
// Må ligge i samme pakke som Tilbakekrevingsbehandling (siden det er et sealed interface), men trenger ikke ligge i samme mappe.

package tilbakekreving.domain

import dokument.domain.brev.Brevvalg
import tilbakekreving.domain.vurdert.Vurderinger

sealed interface ErUtfylt : Tilbakekrevingsbehandling {
    override val månedsvurderinger: Vurderinger
    override val vedtaksbrevvalg: Brevvalg.SaksbehandlersValg
}
