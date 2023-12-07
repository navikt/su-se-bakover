package tilbakekreving.domain.iverksett

import arrow.core.Nel
import no.nav.su.se.bakover.common.CorrelationId
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.hendelse.domain.Hendelsesversjon
import no.nav.su.se.bakover.hendelse.domain.SakshendelseCommand
import tilbakekreving.domain.TilbakekrevingsbehandlingId
import java.util.UUID

data class IverksettTilbakekrevingsbehandlingCommand(
    override val sakId: UUID,
    override val utførtAv: NavIdentBruker.Attestant,
    override val correlationId: CorrelationId,
    override val brukerroller: Nel<Brukerrolle>,
    val tilbakekrevingsbehandlingId: TilbakekrevingsbehandlingId,
    val klientensSisteSaksversjon: Hendelsesversjon,
) : SakshendelseCommand
