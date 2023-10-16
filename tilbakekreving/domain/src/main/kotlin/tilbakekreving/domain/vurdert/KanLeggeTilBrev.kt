@file:Suppress("PackageDirectoryMismatch")
// Må ligge i samme pakke som Tilbakekrevingsbehandling (siden det er et sealed interface), men trenger ikke ligge i samme mappe.

package tilbakekreving.domain

import dokument.domain.brev.Brevvalg
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import tilbakekreving.domain.vurdert.Månedsvurderinger

sealed interface KanLeggeTilBrev : KanEndres {
    override val månedsvurderinger: Månedsvurderinger?
    override val vedtaksbrevvalg: Brevvalg.SaksbehandlersValg?

    fun oppdaterVedtaksbrev(
        hendelseId: HendelseId,
        vedtaksbrevvalg: Brevvalg.SaksbehandlersValg,
    ): UnderBehandling.Utfylt

    override fun erÅpen() = true
}
