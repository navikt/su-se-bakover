package tilbakekreving.domain.vurdert

import no.nav.su.se.bakover.common.CorrelationId
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.hendelse.domain.SakshendelseCommand
import tilbakekreving.domain.TilbakekrevingsbehandlingId
import java.util.UUID

data class ForhåndsvisVedtaksbrevCommand(
    override val sakId: UUID,
    val behandlingId: TilbakekrevingsbehandlingId,
    override val correlationId: CorrelationId?,
    override val utførtAv: NavIdentBruker.Saksbehandler,
    override val brukerroller: List<Brukerrolle>,
) : SakshendelseCommand
