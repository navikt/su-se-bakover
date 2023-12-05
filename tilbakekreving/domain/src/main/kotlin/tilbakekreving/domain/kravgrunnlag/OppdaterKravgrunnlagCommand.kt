package tilbakekreving.domain.kravgrunnlag

import no.nav.su.se.bakover.common.CorrelationId
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.hendelse.domain.Hendelsesversjon
import no.nav.su.se.bakover.hendelse.domain.SakshendelseCommand
import tilbakekreving.domain.TilbakekrevingsbehandlingId
import java.util.UUID

data class OppdaterKravgrunnlagCommand(
    override val sakId: UUID,
    override val correlationId: CorrelationId?,
    override val brukerroller: List<Brukerrolle>,
    val oppdatertAv: NavIdentBruker.Saksbehandler,
    val behandlingId: TilbakekrevingsbehandlingId,
    val klientensSisteSaksversjon: Hendelsesversjon,
) : SakshendelseCommand {
    override val utførtAv: NavIdentBruker = oppdatertAv
}
