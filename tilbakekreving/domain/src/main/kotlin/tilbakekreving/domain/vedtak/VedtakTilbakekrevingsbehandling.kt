package tilbakekreving.domain.vedtak

import dokument.domain.Dokumenttilstand
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.tid.Tidspunkt
import tilbakekreving.domain.IverksattTilbakekrevingsbehandling
import vedtak.domain.Vedtak
import java.util.UUID

class VedtakTilbakekrevingsbehandling(
    override val id: UUID,
    override val opprettet: Tidspunkt,
    override val saksbehandler: NavIdentBruker.Saksbehandler,
    override val attestant: NavIdentBruker.Attestant,
    override val dokumenttilstand: Dokumenttilstand,
    override val behandling: IverksattTilbakekrevingsbehandling,
) : Vedtak {
    override val skalSendeBrev: Boolean
        get() = behandling.vedtaksbrevvalg.skalSendeBrev()

    override fun erAvbrutt(): Boolean = false
}
