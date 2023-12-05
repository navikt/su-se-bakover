package tilbakekreving.domain.notat

import no.nav.su.se.bakover.common.CorrelationId
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.domain.NonBlankString
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.hendelse.domain.Hendelsesversjon
import no.nav.su.se.bakover.hendelse.domain.SakshendelseCommand
import tilbakekreving.domain.TilbakekrevingsbehandlingId
import java.util.UUID

data class OppdaterNotatCommand(
    override val sakId: UUID,
    override val correlationId: CorrelationId?,
    override val brukerroller: List<Brukerrolle>,
    override val utførtAv: NavIdentBruker.Saksbehandler,
    val notat: NonBlankString?,
    val behandlingId: TilbakekrevingsbehandlingId,
    val klientensSisteSaksversjon: Hendelsesversjon,
) : SakshendelseCommand
