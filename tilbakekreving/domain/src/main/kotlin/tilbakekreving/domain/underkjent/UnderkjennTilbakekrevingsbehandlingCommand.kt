package tilbakekreving.domain.underkjent

import arrow.core.Nel
import no.nav.su.se.bakover.common.CorrelationId
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.domain.attestering.Attestering
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.hendelse.domain.Hendelsesversjon
import no.nav.su.se.bakover.hendelse.domain.SakshendelseCommand
import tilbakekreving.domain.TilbakekrevingsbehandlingId
import java.util.UUID

data class UnderkjennTilbakekrevingsbehandlingCommand(
    override val sakId: UUID,
    val behandlingsId: TilbakekrevingsbehandlingId,
    val utførtAv: NavIdentBruker.Attestant,
    override val correlationId: CorrelationId,
    override val brukerroller: Nel<Brukerrolle>,
    val klientensSisteSaksversjon: Hendelsesversjon,
    val grunn: Attestering.Underkjent.Grunn,
    val kommentar: String,
) : SakshendelseCommand {
    override val ident: NavIdentBruker = utførtAv
}
