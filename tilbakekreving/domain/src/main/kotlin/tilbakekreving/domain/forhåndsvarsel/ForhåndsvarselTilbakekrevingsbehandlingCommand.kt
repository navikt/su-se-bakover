package tilbakekreving.domain.forhåndsvarsel

import arrow.core.Nel
import no.nav.su.se.bakover.common.CorrelationId
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.hendelse.domain.Hendelsesversjon
import no.nav.su.se.bakover.hendelse.domain.SakshendelseCommand
import tilbakekreving.domain.TilbakekrevingsbehandlingId
import java.util.UUID

data class ForhåndsvarselTilbakekrevingsbehandlingCommand(
    override val sakId: UUID,
    val behandlingId: TilbakekrevingsbehandlingId,
    override val utførtAv: NavIdentBruker.Saksbehandler,
    override val correlationId: CorrelationId,
    override val brukerroller: Nel<Brukerrolle>,
    val klientensSisteSaksversjon: Hendelsesversjon,
    val fritekst: String,
) : SakshendelseCommand

data class ForhåndsvisForhåndsvarselTilbakekrevingsbehandlingCommand(
    override val sakId: UUID,
    val behandlingId: TilbakekrevingsbehandlingId,
    override val utførtAv: NavIdentBruker.Saksbehandler,
    override val correlationId: CorrelationId,
    override val brukerroller: Nel<Brukerrolle>,
    val klientensSisteSaksversjon: Hendelsesversjon,
    val fritekst: String?,
) : SakshendelseCommand
